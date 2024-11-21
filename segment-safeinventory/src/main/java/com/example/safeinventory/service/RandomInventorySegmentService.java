package com.example.safeinventory.service;

import com.example.safeinventory.mapper.InventorySegmentMapper;
import com.example.safeinventory.model.InventorySegmentModel;
import com.example.safeinventory.strategy.SegmentSelectionStrategy;
import com.example.safeinventory.strategy.SegmentStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


@Service
public class RandomInventorySegmentService {

    private static final Logger logger = LoggerFactory.getLogger(RandomInventorySegmentService.class);


    @Autowired
    private InventorySegmentMapper inventorySegmentMapper;

    @Autowired
    RedisOperationService redisOperationService;

    private static final String MERGE_LOCK_KEY = "merge:lock:";
    private static final int MERGE_LOCK_TIMEOUT = 10000; // 10秒

    private static final int SEGMENT_STOCK = 4;

    private static final int MAX_RETRY_TIMES = 3;  // 最大重试次数

    @Autowired
    private SegmentStrategyFactory strategyFactory;

    /**
     * 动态库存扣减入口
     */
    @Transactional
    public boolean reduceInventory(int productId, int quantity) {
        // 1. 获取库存状态
        InventoryStatus status = getInventoryStatus(productId);
        if (!status.isValid() || status.getTotalAvailable() < quantity) {
            logger.warn("库存状态不可用 productId:{}, status:{}", productId, status);
            return false;
        }

        // 2. 尝试直接扣减
        SegmentSelectionStrategy strategy = strategyFactory.getStrategy(
                SegmentStrategyFactory.StrategyType.BEST_MATCH);
        
        InventorySegmentModel selectedSegment = strategy.selectSegment(status.getSegments(), quantity);
        
        // 3. 如果没有找到合适的分段，才触发合并
        if (selectedSegment == null) {
            logger.info("未找到合适分段，触发合并 productId:{}, quantity:{}", productId, quantity);
            triggerAsyncMerge(productId);

            // 等待短暂时间让合并生效
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 合并后重试
            status = getInventoryStatus(productId);
            selectedSegment = strategy.selectSegment(status.getSegments(), quantity);
        }

        // 4. 尝试在选中的分段中扣减
        if (selectedSegment != null) {
            boolean success = doReduceInventoryInSegment(selectedSegment, quantity);
            if (!success) {
                logger.warn("扣减失败， productId:{}, segmentId:{}",
                    productId, selectedSegment.getSegmentId());
            }
            return success;
        }

        logger.warn("未找到合适分段 productId:{}, quantity:{}", productId, quantity);
        return false;
    }

    /**
     * 获取当前库存状态
     */
    private InventoryStatus getInventoryStatus(int productId) {
        List<InventorySegmentModel> segments =
                inventorySegmentMapper.getSegmentsByProductId(productId);

        if (segments.isEmpty()) {
            return new InventoryStatus(false, Collections.emptyList(), 0);
        }

        int totalAvailable = segments.stream()
                .mapToInt(InventorySegmentModel::getAvailableStock)
                .sum();

        return new InventoryStatus(true, segments, totalAvailable);
    }

    /**
     * 库存状态封装类
     */
    private static class InventoryStatus {
        private final boolean valid;
        private final List<InventorySegmentModel> segments;
        private final int totalAvailable;

        public InventoryStatus(boolean valid, List<InventorySegmentModel> segments,
                               int totalAvailable) {
            this.valid = valid;
            this.segments = segments;
            this.totalAvailable = totalAvailable;
        }

        public boolean isValid() {
            return valid;
        }

        public List<InventorySegmentModel> getSegments() {
            return segments;
        }

        public int getTotalAvailable() {
            return totalAvailable;
        }

        @Override
        public String toString() {
            return "InventoryStatus{" +
                    "valid=" + valid +
                    ", segments=" + segments +
                    ", totalAvailable=" + totalAvailable +
                    '}';
        }
    }

    /**
     * 在选定分段中执行库存扣减
     */
    @Transactional
    public boolean doReduceInventoryInSegment(
            InventorySegmentModel segment, int quantity) {

        logger.info("在分段中扣减库存 productId:{}, segmentId:{}, quantity:{}",
                segment.getProductId(), segment.getSegmentId(), quantity);

        // 乐观锁方式更新库存
        int result = inventorySegmentMapper.reduceAvailableStockWithVersion(
                segment.getProductId(),
                segment.getSegmentId(),
                quantity,
                segment.getVersion()
        );

        if (result != 1) {
            logger.warn("扣减失败 productId:{}, segmentId:{}",
                    segment.getProductId(), segment.getSegmentId());
            return false;
        }

        // 记录库存变更
        logInventoryChange(segment, quantity);

        return true;
    }

    /**
     * 记录库存变更信息，用于后续分析和优化
     */
    private void logInventoryChange(InventorySegmentModel segment, int quantity) {
        logger.info("库存变更 productId:{}, segmentId:{}, " +
                        "beforeStock:{}, reduceQuantity:{}, afterStock:{}",
                segment.getProductId(),
                segment.getSegmentId(),
                segment.getAvailableStock(),
                quantity,
                segment.getAvailableStock() - quantity);
    }


    /**
     * 触发异步合并
     */
    public void triggerAsyncMerge(int productId) {
        String mergeLockKey = MERGE_LOCK_KEY + productId;

        // 1. 尝试获取合并锁
        if (!redisOperationService.acquireLock(mergeLockKey,
                String.valueOf(productId), MERGE_LOCK_TIMEOUT)) {
            logger.info("其他线程正在进行合并，跳过 productId:{}", productId);
            return;
        }

        try {
            // 2. 获取所有分段
            List<InventorySegmentModel> segments =
                    inventorySegmentMapper.getSegmentsByProductId(productId);

            // 3. 计算总可用库存
            int totalAvailable = segments.stream()
                    .mapToInt(InventorySegmentModel::getAvailableStock)
                    .sum();

            if (totalAvailable == 0) {
                logger.warn("所有分段库存已耗尽 productId:{}", productId);
                return;
            }

            // 4. 重新分配库存
            redistributeStock(productId, totalAvailable);

        } catch (Exception e) {
            logger.error("合并库存失败 productId:{}, error:{}", productId, e.getMessage());
        } finally {
            redisOperationService.releaseLock(mergeLockKey, String.valueOf(productId));
        }
    }

    /**
     * 重新分配库存到固定大小的分段
     */
    @Transactional
    public boolean redistributeStock(int productId, int totalStock) {
        try {
            // 1. 计算需要的分段数
            int segmentCount = (int) Math.ceil((double) totalStock / SEGMENT_STOCK);

            // 2. 获取当前最大的segmentId
            Integer maxSegmentId = inventorySegmentMapper.getMaxSegmentId(productId);
            int startSegmentId = (maxSegmentId == null) ? 1 : maxSegmentId + 1;

            // 3. 将旧的分段标记为无效
            inventorySegmentMapper.invalidateSegments(productId);
            logger.info("标记旧分段为无效 productId:{}", productId);

            // 4. 创建新的分段
            List<InventorySegmentModel> newSegments = new ArrayList<>();
            for (int i = 0; i < segmentCount; i++) {
                int stockForSegment = Math.min(SEGMENT_STOCK, totalStock);
                totalStock -= stockForSegment;

                InventorySegmentModel segment = new InventorySegmentModel();
                segment.setProductId(productId);
                segment.setSegmentId(startSegmentId + i);  // 使用递增的segmentId
                segment.setTotalStock(stockForSegment);
                segment.setAvailableStock(stockForSegment);
                segment.setStatus(1);  // 设置状态为有效
                newSegments.add(segment);
            }

            // 5. 批量插入新分段
            inventorySegmentMapper.batchInsert(newSegments);

            logger.info("重新分配库存成功 productId:{}, segmentCount:{}, startSegmentId:{}",
                    productId, segmentCount, startSegmentId);
            return true;

        } catch (Exception e) {
            logger.error("重新分配库存失败 productId:{}, error:{}", productId, e.getMessage());
            throw new RuntimeException("重新分配库存失败", e);
        }
    }

}
