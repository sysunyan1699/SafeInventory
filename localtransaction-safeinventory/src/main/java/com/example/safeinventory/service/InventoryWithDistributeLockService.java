package com.example.safeinventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryWithDistributeLockService {
    private static final Logger logger = LoggerFactory.getLogger(InventoryWithDistributeLockService.class);


    @Autowired
    RedisDistributedLock redisDistributedLock;


    @Autowired
    InventoryWithVersionService inventoryWithVersionService;

    private static final int EXPIRE_TIME = 5 * 60;

    private static final String LOCK_KEY_PREFIX = "product_lock:";

    public boolean reduceInventory(Integer productId, Integer quantity, String requestId) {
        String lockKey = LOCK_KEY_PREFIX + productId;
        // 模拟获取redis 分布式锁逻辑
        boolean lockAcquired = redisDistributedLock.acquireLock(lockKey, requestId, EXPIRE_TIME);
        if (!lockAcquired) {
            logger.info("未获取到锁 productId: {}, quantity: {}", productId, quantity);
            // 获取锁失败，返回或重试
            return false;
        }
        try {
            return inventoryWithVersionService.reduceInventory(productId, quantity);
        } finally {
            // 如果释放失败则重试或者等待过期
            if (lockAcquired) {
                redisDistributedLock.releaseLock(lockKey, requestId);
            }
        }
    }


}
