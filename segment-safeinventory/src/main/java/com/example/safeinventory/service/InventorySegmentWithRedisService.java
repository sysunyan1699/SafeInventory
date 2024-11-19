package com.example.safeinventory.service;

import com.example.safeinventory.common.RedisReduceStockEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class InventorySegmentWithRedisService {
    private static final Logger logger = LoggerFactory.getLogger(InventorySegmentWithRedisService.class);

    @Autowired
    RedisOperationService redisDistributedLock;


    @Autowired
    InventorySegmentService inventorySegmentService;

    private static final String LOCK_KEY_PREFIX = "product_stock:";

    public boolean reduceInventory(Integer productId, Integer quantity, String requestId) {
        logger.info("reduceInventory productId: {}, quantity: {}", productId, quantity);
        String lockKey = LOCK_KEY_PREFIX + productId;
        List<String> segmentIds = redisDistributedLock.getInventorySegmentIds(lockKey);

        int segmentCount = segmentIds.size();
        if (segmentCount == 0) {
            logger.info("无可用库存段 productId: {}, quantity: {}", productId, quantity);
            return false;
        }
        Random random = new Random();
        int startIndex = random.nextInt(segmentCount); // 使用随机数或其他方式选择起始索引

        // 先随机再轮训所有redis库存字段
        for (int i = 0; i < segmentIds.size(); i++) {
            String segmentId = segmentIds.get((startIndex + i) % segmentIds.size()); // 轮询获取分段
            // 在当前分段扣减库存
            long redisReduceResult = redisDistributedLock.reduceStock(lockKey, segmentId, quantity);

            if (redisReduceResult != RedisReduceStockEnum.REDUCE_SUCCESS.getValue()) {
                logger.warn("库存扣减失败 productId: {}, quantity: {},segmentId:{}, reduceResult:{}",
                        productId, quantity, segmentId, redisReduceResult);
                continue;
            }
            logger.info("redisDistributedLock.reduceStock productId: {}, quantity: {},segmentId:{}",
                    productId, quantity, segmentId);

            // 数据库扣减库存，如果失败则不再重试，返回失败结果
            boolean isSuccess = inventorySegmentService.doReduceInventoryInSegmentV2(productId, Integer.parseInt(segmentId), quantity);
            logger.info("doReduceInventory productId: {}, quantity: {},segmentId:{}, reduceResult:{}",
                    productId, Integer.parseInt(segmentId), quantity, isSuccess);

            if (isSuccess) {
                return true; // 扣减成功
            } else {
                redisDistributedLock.rollbackInventory(lockKey, segmentId, quantity);
                return false;
            }
        }
        return false;
    }

}
