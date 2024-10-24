package com.example.safeinventory.service;

import com.example.safeinventory.common.BusinessException;
import com.example.safeinventory.mapper.InventoryMapper;
import com.example.safeinventory.model.InventoryModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class InventoryByCheckingStockService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryByCheckingStockService.class);

    @Autowired
    private InventoryMapper inventoryMapper;

    // 执行不同业务场景下，具体的业务逻辑，商品售卖就是创建订单数据， 营销发券就是生成用户券数
    @Autowired
    private BusinessService businessService;

    /**
     * 如果希望 @Transactional(rollbackFor = Exception.class) 能保证事务回滚，
     * 需要让 BusinessException 继承自 RuntimeException，这样 Spring 会默认回滚。
     * 如果 BusinessException 继承自 Throwable 或 Exception，则需要在 @Transactional 中显式指定该异常来触发回滚。
     * 如 @Transactional(rollbackFor = {Exception.class, BusinessException.class})
     *
     * @param productId
     * @param quantity
     * @return
     * @throws BusinessException
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean reduceInventory(Integer productId, Integer quantity) {
        logger.info("reduceInventory begin productId: {}, quantity: {}", productId, quantity);

        InventoryModel inventory = inventoryMapper.selectByProductId(productId);
        if (inventory.getAvailableStock() < quantity) {
            logger.warn("库存不足: productId={}, requestedQuantity={}, availableStock={}",
                    productId, quantity, inventory.getAvailableStock());
            throw new BusinessException("库存不足，无法扣减库存");
        }
        // 扣减库存 - 库存条件控制
        int updatedRows = inventoryMapper.reduceAvailableStockWithCheckingStock(
                productId,
                quantity,
                quantity
        );

        if (updatedRows == 0) {
            logger.warn("库存扣减失败  productId: {}, quantity: {}", productId, quantity);
            throw new BusinessException("库存扣减失败");
        }
        // 执行业务逻辑
        boolean result = businessService.createBusinessDate();

        if (!result) {
            // 抛出异常以便事务回滚
            logger.warn("业务逻辑执行失败  productId: {}, quantity: {}", productId, quantity);
            throw new BusinessException("业务逻辑执行失败");
        }
        return true;
    }
}
