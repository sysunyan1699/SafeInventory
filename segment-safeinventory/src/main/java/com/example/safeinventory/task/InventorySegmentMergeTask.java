package com.example.safeinventory.task;

import com.example.safeinventory.mapper.InventorySegmentMapper;
import com.example.safeinventory.model.InventorySegmentModel;
import com.example.safeinventory.service.RandomInventorySegmentService;
import com.example.safeinventory.service.RedisOperationService;
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

    private static final String MERGE_TASK_LOCK_KEY = "merge:task:lock";
    private static final int MERGE_TASK_LOCK_TIMEOUT = 300; // 5分钟
    private static final double FRAGMENT_THRESHOLD = 0.3; // 碎片化阈值，30%

    @Autowired
    private InventorySegmentMapper inventorySegmentMapper;

    @Autowired
    private RandomInventorySegmentService randomInventorySegmentService;

    @Autowired
    private RedisOperationService redisOperationService;

    /**
     * 每小时执行一次合并检查
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void checkAndMergeSegments() {
        String taskId = String.valueOf(System.currentTimeMillis());
        
        // 1. 获取分布式锁
        if (!redisOperationService.acquireLock(MERGE_TASK_LOCK_KEY, taskId, MERGE_TASK_LOCK_TIMEOUT * 1000)) {
            logger.info("其他实例正在执行合并任务，本次跳过");
            return;
        }

        try {
            // 2. 获取所有有效的库存分段
            List<InventorySegmentModel> allSegments = 
                inventorySegmentMapper.getAllValidSegments();

            // 3. 按商品ID分组
            Map<Integer, List<InventorySegmentModel>> segmentsByProduct = 
                allSegments.stream()
                    .collect(Collectors.groupingBy(InventorySegmentModel::getProductId));

            // 4. 检查每个商品的分段情况
            for (Map.Entry<Integer, List<InventorySegmentModel>> entry : segmentsByProduct.entrySet()) {
                int productId = entry.getKey();
                List<InventorySegmentModel> segments = entry.getValue();

                if (needsMerge(segments)) {
                    logger.info("商品{}需要进行分段合并, 当前分段数:{}", productId, segments.size());
                    try {
                        randomInventorySegmentService.triggerAsyncMerge(productId);
                    } catch (Exception e) {
                        logger.error("商品{}合并失败", productId, e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("合并任务执行失败", e);
        } finally {
            redisOperationService.releaseLock(MERGE_TASK_LOCK_KEY, taskId);
        }
    }

    /**
     * 判断是否需要合并
     * 1. 碎片化程度超过阈值
     * 2. 存在空闲分段（可用库存为0）
     */
    private boolean needsMerge(List<InventorySegmentModel> segments) {
        if (segments.isEmpty()) {
            return false;
        }

        // 计算碎片化程度
        int fragmentedSegments = 0;
        int emptySegments = 0;
        int totalSegments = segments.size();

        for (InventorySegmentModel segment : segments) {
            if (segment.getAvailableStock() == 0) {
                emptySegments++;
                continue;
            }
            
            if (segment.getAvailableStock() < segment.getTotalStock()) {
                fragmentedSegments++;
            }
        }

        // 碎片化比率
        double fragmentRatio = (double) fragmentedSegments / totalSegments;
        
        // 空闲比率
        double emptyRatio = (double) emptySegments / totalSegments;

        return fragmentRatio > FRAGMENT_THRESHOLD || emptyRatio > 0.2;
    }
} 