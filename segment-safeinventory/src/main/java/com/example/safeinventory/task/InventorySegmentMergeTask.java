package com.example.safeinventory.task;

import com.example.safeinventory.mapper.InventorySegmentMapper;
import com.example.safeinventory.model.InventorySegmentModel;
import com.example.safeinventory.service.RandomInventorySegmentService;
import com.example.safeinventory.service.RedisOperationService;
import com.example.safeinventory.strategy.MergeCheckStrategy;
import com.example.safeinventory.strategy.MergeStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class InventorySegmentMergeTask {
    private static final Logger logger = LoggerFactory.getLogger(InventorySegmentMergeTask.class);

    @Autowired
    private InventorySegmentMapper inventorySegmentMapper;

    @Autowired
    private RandomInventorySegmentService randomInventorySegmentService;

    private final MergeCheckStrategy mergeStrategy;

    public InventorySegmentMergeTask(MergeStrategyFactory mergeStrategyFactory) {
        this.mergeStrategy = mergeStrategyFactory.getStrategy(
                MergeStrategyFactory.MergeStrategyType.USAGE_RATIO);
    }

    /**
     * 每小时执行一次合并检查
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkAndMergeSegments() {
        // 1. 获取所有有效的库存分段
        List<InventorySegmentModel> allSegments =
                inventorySegmentMapper.getAllValidSegments();

        // 2. 按商品ID分组
        Map<Integer, List<InventorySegmentModel>> segmentsByProduct =
                allSegments.stream()
                        .collect(Collectors.groupingBy(InventorySegmentModel::getProductId));

        // 3. 检查每个商品的分段情况
        for (Map.Entry<Integer, List<InventorySegmentModel>> entry : segmentsByProduct.entrySet()) {
            int productId = entry.getKey();
            List<InventorySegmentModel> segments = entry.getValue();

            if (mergeStrategy.isNeedMerge(segments)) {
                logger.info("商品{}需要进行分段合并, 当前分段数:{}", productId, segments.size());
                try {
                    randomInventorySegmentService.triggerStandardMerge(productId);
                } catch (Exception e) {
                    logger.error("商品{}合并失败", productId, e);
                }
            }
        }
    }
} 