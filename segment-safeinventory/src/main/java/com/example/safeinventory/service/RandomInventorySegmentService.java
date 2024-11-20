package com.example.safeinventory.service;

import com.example.safeinventory.mapper.InventoryMapper;
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
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventorySegmentMapper inventorySegmentMapper;

    @Autowired
    RedisOperationService redisOperationService;

    private static final String MERGE_LOCK_KEY = "merge:lock:";
    private static final int MERGE_LOCK_TIMEOUT = 10000; // 10秒

    private static final int SEGMENT_STOCK = 4;

    private static final int MAX_RETRY_TIMES = 3;  // 最大重试次数
    private static final double FRAGMENT_THRESHOLD = 0.3; // 碎片化阈值，30%

    @Autowired
    private SegmentStrategyFactory strategyFactory;

    /**
     * 动态库存扣减入口
     */
    @Transactional
    public boolean reduceInventory(int productId, int quantity) {
        // 1. 尝试直接扣减
        boolean success = tryReduceInventory(productId, quantity);
        if (success) {
            return true;
        }

        // 2. 触发合并并重试
        logger.info("直接扣减失败，尝试合并后重试 productId:{}, quantity:{}",
                productId, quantity);

        triggerAsyncMerge(productId);

        // 等待短暂时间让合并生效
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. 合并后重试
        success = tryReduceInventory(productId, quantity);
        if (!success) {
            logger.warn("合并后仍未找到合适分段 productId:{}, quantity:{}",
                    productId, quantity);
        }

        return success;
    }

    /**
     * 尝试在当前库存状态下完成扣减
     */
    private boolean tryReduceInventory(int productId, int quantity) {
        InventoryStatus status = getInventoryStatus(productId);
        if (!status.isValid() || status.getTotalAvailable() < quantity) {
            logger.warn("库存状态不可用 productId:{}, status:{}", productId, status);
            return false;
        }

        // 使用工厂获取策略实例
        SegmentSelectionStrategy strategy = strategyFactory.getStrategy(
                SegmentStrategyFactory.StrategyType.BEST_MATCH);

        // 选择分段
        InventorySegmentModel selectedSegment = strategy.selectSegment(
                status.getSegments(), quantity);

        if (selectedSegment != null) {
            return doReduceInventoryInSegment(selectedSegment, quantity);
        }

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
            return String.format("InventoryStatus{valid=%s, segments=%d, total=%d}",
                    valid, segments.size(), totalAvailable);
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
            logger.warn("扣减失败，版本号不匹配 productId:{}, segmentId:{}",
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
     * 带重试的库存扣减
     */
    public boolean reduceInventoryWithRetry(int productId, int quantity) {
        int retryCount = 0;
        do {
            // 1. 尝试扣减
            boolean success = reduceInventory(productId, quantity);
            if (success) {
                return true;
            }

            // 2. 检查库存碎片化程度
            if (isHighlyFragmented(productId)) {
                // 3. 触发异步合并
                triggerAsyncMerge(productId);

                // 4. 等待短暂时间
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            retryCount++;
        } while (retryCount < MAX_RETRY_TIMES);

        return false;
    }

    /**
     * 检查库存碎片化程度
     */
    private boolean isHighlyFragmented(int productId) {
        List<InventorySegmentModel> segments =
                inventorySegmentMapper.getSegmentsByProductId(productId);

        if (segments.isEmpty()) {
            return false;
        }

        // 计算碎片化程度
        int fragmentedSegments = 0;
        for (InventorySegmentModel segment : segments) {
            if (segment.getAvailableStock() > 0 &&
                    segment.getAvailableStock() < SEGMENT_STOCK) {
                fragmentedSegments++;
            }
        }

        double fragmentRatio = (double) fragmentedSegments / segments.size();
        logger.info("库存碎片化程度 productId:{}, ratio:{}", productId, fragmentRatio);

        return fragmentRatio > FRAGMENT_THRESHOLD;
    }

    /**
     * 触发异步合并
     */
    public void triggerAsyncMerge(int productId) {
        String mergeLockKey = MERGE_LOCK_KEY + productId;

        // 1. 尝试获取合并锁（非阻塞）
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