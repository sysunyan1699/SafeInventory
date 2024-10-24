package com.example.safeinventory.service;


import com.example.safeinventory.constants.ReservationStatus;
import com.example.safeinventory.model.InventoryReservationLogModel;
import com.example.safeinventory.mapper.InventoryMapper;
import com.example.safeinventory.mapper.InventoryReservationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class InventoryService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryService.class);

    @Resource
    BusinessService businessService;

    @Resource
    InventoryMapper inventoryMapper;
    @Resource
    InventoryReservationLogMapper inventoryReservationLogMapper;

    @Resource
    RedisDistributedLock redisDistributedLock;


    @Resource
    InventoryInternalService inventoryInternalService;

    private static final int EXPIRE_TIME = 5 * 60;

    private static final String LOCK_KEY_PREFIX = "product_lock:";

    // TCC-try, 该步骤需要考虑并发，控制超卖问题，解决方案与本地事务中一直，这里以分布式锁方案为例
    public boolean reserveInventory(Integer productId, Integer quantity, String requestId) {
        String lockKey = LOCK_KEY_PREFIX + productId;
        // 模拟获取redis 分布式锁逻辑
        boolean lockAcquired = redisDistributedLock.acquireLock(lockKey, requestId, EXPIRE_TIME);
        if (!lockAcquired) {
            logger.info("未获取到锁 productId: {}, quantity: {}, requestId:{}", productId, quantity, requestId);
            // 获取锁失败，返回或重试
            return false;
        }
        try {
            return inventoryInternalService.reserveInventory(productId, quantity, requestId);
        } finally {
            // 如果释放失败则重试或者等待过期
            if (lockAcquired) {
                redisDistributedLock.releaseLock(lockKey, requestId);
            }
        }
    }

    // TCC-confirm
    public boolean confirmReservedInventory(Integer productId, String requestId) {
        InventoryReservationLogModel model = inventoryReservationLogMapper.selectByRequestId(requestId);
        if (model.getReservationStatus() != ReservationStatus.PENDING.getValue()) {
            logger.warn("status is not pending ,can not confirm the reserved stock, requestId:{}", requestId);
            return false;
        }
        return inventoryInternalService.confirmReservedInventory(productId,
                requestId,
                model.getVersion(),
                model.getReservationQuantity());

    }

    /**
     * 再确认扣减库存时，需要执行业务逻辑，且业务逻辑数据与 库存表流水表不在同一个数据库中，
     *
     * @param productId
     * @param requestId
     * @return
     */

    public boolean confirmReservedInventoryWithBusinessLogic(Integer productId, String requestId) {
        InventoryReservationLogModel model = inventoryReservationLogMapper.selectByRequestId(requestId);
        if (model.getReservationStatus() != ReservationStatus.PENDING.getValue()) {
            logger.warn("status is not pending ,can not confirm the reserved stock, requestId:{}", requestId);
            return false;
        }

        //先插入业务数据
        boolean businessResult = businessService.createBusinessDate();

        if (!businessResult) {
            logger.warn("业务逻辑执行失败，requestId:{}，productId:{} ", requestId, productId);
            return false;
        }

        boolean confirmResult = false;
        try {
            confirmResult = inventoryInternalService.confirmReservedInventory(productId,
                    requestId,
                    model.getVersion(),
                    model.getReservationQuantity());
        } catch (Exception e) {
            logger.warn("业务逻辑执行失败，requestId:{}，productId:{}, error:{}", requestId, productId, e);
        }

        return confirmResult;
    }


    // TCC-cancel
    public boolean rollbackReservedInventory(Integer productId, String requestId) {
        return inventoryInternalService.rollbackReservedInventory(productId, requestId);
    }


}
