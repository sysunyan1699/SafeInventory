package com.example.safeinventory.service;

import com.example.safeinventory.common.RedisReduceStockEnum;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


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

    @Autowired
    private SegmentStrategyFactory strategyFactory;

    private static final String SEGMENTS_CACHE_KEY = "inventory:segments:";
    private static final int SEGMENTS_CACHE_EXPIRE = 24 * 60 * 60; // 24小时

    private static final String SEGMENT_STOCK_KEY = "inventory:segments:stock:";

    /**
     * 动态库存扣减入口
     */
    @Transactional
    public boolean reduceInventory(int productId, int quantity) {
        // 1. 获取库存状态
        InventoryStatus status = getInventorySegment(productId);
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

            // 根据请求量选择合适的合并策略,并同步等待重试
            boolean mergeSuccess = triggerMerge(productId, quantity);
            if (!mergeSuccess) {
                return false;
            }
            // 合并后重试
            status = getInventorySegment(productId);
            selectedSegment = strategy.selectSegment(status.getSegments(), quantity);
        }

        // 4. 尝试在选中的分段中扣减
        if (selectedSegment == null) {
            logger.warn("未找到合适分段 productId:{}, quantity:{}", productId, quantity);
            return false;
        }

        boolean success = doReduceInventoryInSegment(selectedSegment, quantity);
        if (!success) {
            logger.info("扣减失败， productId:{}, segmentId:{}",
                    productId, selectedSegment.getSegmentId());
        }
        return success;
    }

    /**
     * 根据请求量选择并触发合适的合并策略
     */
    private boolean triggerMerge(int productId, int quantity) {
        if (quantity > SEGMENT_STOCK) {
            logger.info("大额请求，使用特殊合并策略 productId:{}, quantity:{}", productId, quantity);
            return triggerLargeQuantityMerge(productId, quantity);
        }
        logger.info("小额请求，使用标准合并策略 productId:{}, quantity:{}", productId, quantity);
        return triggerStandardMerge(productId);
    }


    /**
     * 特殊合并策略 - 用于大额请求
     * 尝试创建足够大的分段来满足请求
     */
    private boolean triggerLargeQuantityMerge(int productId, int quantity) {
        return triggerMergeWithLock(productId, totalStock ->
                redistributeStockWithStrategy(productId, totalStock, quantity, true));
    }

    /**
     * 通用的合并锁控制逻辑
     */
    private <T> T triggerMergeWithLock(int productId, Function<Integer, T> mergeFunction) {
        String mergeLockKey = MERGE_LOCK_KEY + productId;

        if (!redisOperationService.acquireLock(mergeLockKey,
                String.valueOf(productId), MERGE_LOCK_TIMEOUT)) {
            logger.info("其他线程正在进行合并，跳过 productId:{}", productId);
            return null;
        }

        try {
            // 1. 获取所有分段
            List<InventorySegmentModel> segments =
                    inventorySegmentMapper.getSegmentsByProductId(productId);

            // 2. 计算总可用库存
            int totalAvailable = segments.stream()
                    .mapToInt(InventorySegmentModel::getAvailableStock)
                    .sum();

            if (totalAvailable == 0) {
                logger.warn("所有分段库存已耗尽 productId:{}", productId);
                return null;
            }

            // 3. 执行合并函数
            return mergeFunction.apply(totalAvailable);

        } catch (Exception e) {
            logger.error("合并库存失败 productId:{}, error:{}", productId, e.getMessage());
            throw e;
        } finally {
            redisOperationService.releaseLock(mergeLockKey, String.valueOf(productId));
        }
    }

    /**
     * 统一的库存重分配逻辑
     *
     * @param productId        商品ID
     * @param totalStock       总库存
     * @param firstSegmentSize 第一个分段的大小（大额请求时等于请求量，标准合并时等于SEGMENT_STOCK）
     * @param isLargeQuantity  是否是大额请求
     */
    @Transactional
    public boolean redistributeStockWithStrategy(int productId, int totalStock,
                                                 int firstSegmentSize, boolean isLargeQuantity) {
        try {
            // 1. 获取当前最大的segmentId
            Integer maxSegmentId = inventorySegmentMapper.getMaxSegmentId(productId);
            int startSegmentId = (maxSegmentId == null) ? 1 : maxSegmentId + 1;

            // 2. 将旧的分段标记为无效
            inventorySegmentMapper.invalidateSegments(productId);
            logger.info("标记旧分段无效 productId:{}", productId);

            List<InventorySegmentModel> newSegments = new ArrayList<>();

            // 3. 创建第一个分段（可能是大分段或标准分段）
            InventorySegmentModel firstSegment = new InventorySegmentModel();
            firstSegment.setProductId(productId);
            firstSegment.setSegmentId(startSegmentId);
            firstSegment.setTotalStock(firstSegmentSize);
            firstSegment.setAvailableStock(firstSegmentSize);
            firstSegment.setStatus(1);
            newSegments.add(firstSegment);

            // 4. 处理剩余库存
            int remainingStock = totalStock - firstSegmentSize;
            if (remainingStock > 0) {
                int segmentCount = (int) Math.ceil((double) remainingStock / SEGMENT_STOCK);
                for (int i = 0; i < segmentCount; i++) {
                    int stockForSegment = Math.min(SEGMENT_STOCK, remainingStock);
                    remainingStock -= stockForSegment;

                    InventorySegmentModel segment = new InventorySegmentModel();
                    segment.setProductId(productId);
                    segment.setSegmentId(startSegmentId + i + 1);
                    segment.setTotalStock(stockForSegment);
                    segment.setAvailableStock(stockForSegment);
                    segment.setStatus(1);
                    newSegments.add(segment);
                }
            }

            // 5. 批量插入新分段
            inventorySegmentMapper.batchInsert(newSegments);

            // 6. 更新缓存
            loadAndCacheSegments(productId);

            logger.info("重新分配库存成功 productId:{}, segmentCount:{}, isLargeQuantity:{}",
                    productId, newSegments.size(), isLargeQuantity);
            return true;

        } catch (Exception e) {
            logger.error("重新分配库存失败 productId:{}, error:{}", productId, e.getMessage());
            throw new RuntimeException("重新分配库存失败", e);
        }
    }

    /**
     * 获取当前库存状态
     */
    private InventoryStatus getInventorySegment(int productId) {
        // 1. 尝试从Redis获取分段信息
        List<InventorySegmentModel> segments = getSegmentsFromCache(productId);

        // 2. 缓存未命中，从数据库加载并缓存
        if (segments == null) {
            segments = loadAndCacheSegments(productId);
        }

        if (segments.isEmpty()) {
            return new InventoryStatus(false, Collections.emptyList(), 0);
        }

        int totalAvailable = segments.stream()
                .mapToInt(InventorySegmentModel::getAvailableStock)
                .sum();

        return new InventoryStatus(true, segments, totalAvailable);
    }

    /**
     * 从Redis获取分段信息
     */
    private List<InventorySegmentModel> getSegmentsFromCache(int productId) {
        String key = SEGMENT_STOCK_KEY + productId;

        // Lua脚本：获取hash的所有字段和值
        String script =
                "local result = {}; " +
                        "local fields = redis.call('hgetall', KEYS[1]); " +
                        "if #fields == 0 then return nil end; " +
                        "for i = 1, #fields, 2 do " +
                        "  table.insert(result, {fields[i], fields[i+1]}); " +
                        "end; " +
                        "return result;";

        List<List<String>> result = (List<List<String>>) redisOperationService.evalScript(
                script,
                Collections.singletonList(key),
                Collections.emptyList()
        );

        if (result == null) {
            return null;
        }

        // 将结果转换为InventorySegmentModel列表
        return result.stream()
                .map(item -> {
                    InventorySegmentModel model = new InventorySegmentModel();
                    model.setProductId(productId);
                    model.setSegmentId(Integer.parseInt(item.get(0)));
                    model.setAvailableStock(Integer.parseInt(item.get(1)));
                    return model;
                })
                .collect(Collectors.toList());
    }

    /**
     * 从数据库加载并缓存分段信息
     */
    private List<InventorySegmentModel> loadAndCacheSegments(int productId) {
        List<InventorySegmentModel> segments =
                inventorySegmentMapper.getSegmentsByProductId(productId);

        if (!segments.isEmpty()) {
            // 转换为Hash结构并缓存
            Map<Integer, Integer> stockMap = segments.stream()
                    .collect(Collectors.toMap(
                            InventorySegmentModel::getSegmentId,
                            InventorySegmentModel::getAvailableStock
                    ));
            setSegmentsStock(productId, stockMap);
        }

        return segments;
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
    public boolean doReduceInventoryInSegment(InventorySegmentModel segment, int quantity) {
        // 1. 先尝试在Redis中扣减
        // 在当前分段扣减库存
        String lockKey = SEGMENTS_CACHE_KEY + segment.getProductId();
        long redisReduceResult = redisOperationService.reduceStock(lockKey, String.valueOf(segment.getSegmentId()), quantity);

        if (redisReduceResult != RedisReduceStockEnum.REDUCE_SUCCESS.getValue()) {
            logger.warn("redis 库存扣减失败 productId: {}, quantity: {},segmentId:{}, reduceResult:{}",
                    segment.getProductId(), quantity, segment.getSegmentId(), redisReduceResult);
            return false;
        }

        // 2. Redis扣减成功后更新数据库
        // todo  redis 数据中没有version
        boolean isSuccess = inventorySegmentMapper.reduceAvailableStockWithVersion(
                segment.getProductId(),
                segment.getSegmentId(),
                quantity,
                segment.getVersion()
        ) == 1;

        if (isSuccess) {
            redisOperationService.rollbackInventory(lockKey, String.valueOf(segment.getSegmentId()), quantity);
            return false;
        }
        return true;
    }


    /**
     * 标准合并策略 - 用于小额请求 & 定时任务合并
     */
    public boolean triggerStandardMerge(int productId) {
        return triggerMergeWithLock(productId, totalStock ->
                redistributeStockWithStrategy(productId, totalStock, SEGMENT_STOCK, false));
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
            logger.info("标记旧分段无效 productId:{}", productId);

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

            // 重新加载并缓存新的分段信息
            loadAndCacheSegments(productId);

            logger.info("新分配库存成功 productId:{}, segmentCount:{}, startSegmentId:{}",
                    productId, segmentCount, startSegmentId);
            return true;

        } catch (Exception e) {
            logger.error("重新分配库存失败 productId:{}, error:{}", productId, e.getMessage());
            throw new RuntimeException("重新分配库存失败", e);
        }
    }

    /**
     * 批量设置分段库存
     */
    private void setSegmentsStock(int productId, Map<Integer, Integer> segmentStocks) {
        String key = SEGMENT_STOCK_KEY + productId;

        // 构建参数列表：[segmentId1, stock1, segmentId2, stock2, ...]
        List<String> args = new ArrayList<>();
        segmentStocks.forEach((segmentId, stock) -> {
            args.add(String.valueOf(segmentId));
            args.add(String.valueOf(stock));
        });

        // Lua脚本：使用HSET一次性设置多个字段
        String script =
                "return redis.call('hset', KEYS[1], unpack(ARGV))";

        redisOperationService.evalScript(
                script,
                Collections.singletonList(key),
                args
        );
    }

}
