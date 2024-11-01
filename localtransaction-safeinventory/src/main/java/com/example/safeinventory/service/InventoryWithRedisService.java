package com.example.safeinventory.service;

import com.example.safeinventory.common.RedisReduceStockEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryWithRedisService {


    private static final Logger logger = LoggerFactory.getLogger(InventoryWithRedisService.class);


    @Autowired
    RedisDistributedLock redisDistributedLock;


    @Autowired
    InventoryForUpdateService inventoryForUpdateService;

    private static final String LOCK_KEY_PREFIX = "product_stock:";

    public boolean reduceInventory(Integer productId, Integer quantity, String requestId) {
        String lockKey = LOCK_KEY_PREFIX + productId;
        // redis 扣减库存
        long redisReduceResult = redisDistributedLock.reduceStock(lockKey, quantity);

        if (redisReduceResult != RedisReduceStockEnum.REDUCE_SUCCESS.getValue()) {
            logger.warn("库存扣减失败 productId: {}, quantity: {},reduceResult:{}", productId, quantity, redisReduceResult);
            return false;

        }

        logger.info("库存扣减成功 productId: {}, quantity: {}", productId, quantity);
        boolean result = inventoryForUpdateService.reduceInventory(productId, quantity);

        if (!result) {
            // 万一redis 库存回滚失败，靠异步同步任务保证 redis库存 与 数据库库存 的一致性
            redisDistributedLock.rollbackStock(lockKey, quantity);
        }
        return result;
    }
}
