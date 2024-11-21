package com.example.safeinventory.strategy;

import com.example.safeinventory.model.InventorySegmentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 基于使用率的合并检查策略
 */
@Component
public class UsageRatioMergeStrategy implements MergeCheckStrategy {
    private static final Logger logger = LoggerFactory.getLogger(UsageRatioMergeStrategy.class);

    private static final double SEGMENT_USAGE_THRESHOLD = 0.5;  // 单个分段使用率阈值
    private static final double FRAGMENT_RATIO_THRESHOLD = 0.3; // 碎片化分段比例阈值
    private static final int MIN_FRAGMENT_COUNT = 2;           // 最小碎片化分段数量

    @Override
    public boolean isNeedMerge(List<InventorySegmentModel> segments) {
        if (segments.isEmpty()) {
            return false;
        }

        int fragmentedSegments = 0;
        for (InventorySegmentModel segment : segments) {
            double usageRatio = (double) segment.getAvailableStock() / segment.getTotalStock();
            if (usageRatio > 0 && usageRatio < SEGMENT_USAGE_THRESHOLD) {
                fragmentedSegments++;
            }
        }

        double fragmentRatio = (double) fragmentedSegments / segments.size();
        
        logger.info("使用率检查策略 totalSegments:{}, fragmentedSegments:{}, fragmentRatio:{}", 
            segments.size(), fragmentedSegments, fragmentRatio);

        return fragmentRatio > FRAGMENT_RATIO_THRESHOLD && fragmentedSegments >= MIN_FRAGMENT_COUNT;
    }
} 