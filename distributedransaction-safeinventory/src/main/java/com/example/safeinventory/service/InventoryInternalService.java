package com.example.safeinventory.service;

import com.example.safeinventory.constants.ReservationStatus;
import com.example.safeinventory.mapper.InventoryMapper;
import com.example.safeinventory.mapper.InventoryReservationLogMapper;
import com.example.safeinventory.model.InventoryModel;
import com.example.safeinventory.model.InventoryReservationLogModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class InventoryInternalService {


    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    @Autowired
    InventoryMapper inventoryMapper;
    @Autowired
    InventoryReservationLogMapper inventoryReservationLogMapper;


    // TCC-try
    @Transactional
    public boolean reserveInventory(Integer productId, Integer quantity, String requestId) {
        InventoryModel inventory = inventoryMapper.selectByProductId(productId);
        if (inventory.getAvailableStock() < quantity) {
            logger.warn("库存不足: productId={}, requestedQuantity={}, availableStock={}",
                    productId, quantity, inventory.getAvailableStock());
            return false;
        }

        //  流水表插入
        InventoryReservationLogModel model = new InventoryReservationLogModel();
        model.setProductId(productId);
        model.setReservationQuantity(quantity);
        model.setRequestId(requestId);
        model.setStatus(ReservationStatus.PENDING.getValue());
        inventoryReservationLogMapper.insertInventoryReservationLog(model);

        // 库存扣减
        int updatedRows = inventoryMapper.reserveStockWithVersion(productId, quantity, inventory.getVersion());

        if (updatedRows == 0) {
            logger.warn("库存扣减失败 productId: {}, quantity: {}, requestId:{}", productId, quantity, requestId);
            throw new RuntimeException("库存扣减失败");
        }
        return true;
    }


    // TCC-confirm
    @Transactional
    public boolean confirmReservedInventory(Integer productId, String requestId, Integer version ,Integer reservationQuantity) {

        // 指定原始status 做乐观锁
        int updateResult = inventoryReservationLogMapper.updateStatus(
                requestId,
                ReservationStatus.CONFIRMED.getValue(),
                version);

        if (updateResult != 1) {
            throw new RuntimeException("流水状态更新失败");
        }

        int rollbackResult = inventoryMapper.confirmStock(productId, reservationQuantity);

        if (rollbackResult != 1) {
            throw new RuntimeException("库存回滚失败");
        }
        return true;

    }


    // TCC-cancel
    @Transactional
    public boolean rollbackReservedInventory(Integer productId, String requestId) {

        InventoryReservationLogModel model = inventoryReservationLogMapper.selectByRequestId(requestId);
        if (model.getStatus() != ReservationStatus.PENDING.getValue()) {
            logger.warn("rollbackReservedInventory is not pending ,can not rollback the reserved stock");
            return false;

        }

        // 指定原始status 做乐观锁
        int updateResult = inventoryReservationLogMapper.updateStatus(
                requestId,
                ReservationStatus.ROLLBACK.getValue(),
                model.getVersion());

        if (updateResult != 1) {
            throw new RuntimeException("流水状态更新失败");
        }

        int rollbackResult = inventoryMapper.rollbackStock(productId, model.getReservationQuantity());

        if (rollbackResult != 1) {
            throw new RuntimeException("库存回滚失败");
        }
        return true;
    }
}
