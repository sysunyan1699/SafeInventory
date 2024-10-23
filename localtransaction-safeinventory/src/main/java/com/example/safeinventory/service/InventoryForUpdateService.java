package com.example.safeinventory.service;

import com.example.safeinventory.common.BusinessException;
import com.example.safeinventory.mapper.InventoryMapper;
import com.example.safeinventory.model.InventoryModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

public class InventoryForUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryForUpdateService.class);

    @Resource
    private InventoryMapper inventoryMapper;

    // 执行不同业务场景下，具体的业务逻辑，商品售卖就是创建订单数据， 营销发券就是生成用户券数
    @Resource
    private BusinessService businessService;
    @Transactional(rollbackFor = Exception.class)
    public boolean doBusiness(Integer productId, Integer quantity) {
        logger.info("doBusiness productId: {}, quantity: {}", productId, quantity);

        // 悲观锁锁住该行数据， 防止其他事务进行修改
        InventoryModel inventory = inventoryMapper.selectByProductIdForUpdate(productId);
        if (inventory.getAvailableStock() < quantity){
            logger.warn("库存不足: productId={}, requestedQuantity={}, availableStock={}",
                    productId, quantity, inventory.getAvailableStock());
            throw new BusinessException("库存不足，无法扣减库存");
        }
        int updatedRows = inventoryMapper.reduceAvailableStock(
                productId,
                quantity
        );

        if (updatedRows == 0) {
            logger.warn("doBusiness 库存扣减失败  productId: {}, quantity: {}", productId, quantity);
            throw new BusinessException("库存扣减失败");
        }

        // 执行业务逻辑
        boolean result  = businessService.createBusinessDate();

        if (!result) {
            // 抛出异常以便事务回滚
            logger.warn("doBusiness 业务逻辑执行失败  productId: {}, quantity: {}", productId, quantity);
            throw new BusinessException("业务逻辑执行失败");
        }
        return true;
    }
}
