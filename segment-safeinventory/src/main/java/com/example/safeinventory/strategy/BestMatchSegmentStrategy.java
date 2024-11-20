package com.example.safeinventory.strategy;

import com.example.safeinventory.model.InventorySegmentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 最佳匹配策略实现
 */
@Component
public class BestMatchSegmentStrategy implements SegmentSelectionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(BestMatchSegmentStrategy.class);

    @Override
    public InventorySegmentModel selectSegment(List<InventorySegmentModel> segments, int quantity) {
        List<InventorySegmentModel> exactMatches = new ArrayList<>();
        List<InventorySegmentModel> closeMatches = new ArrayList<>();
        List<InventorySegmentModel> largestMatches = new ArrayList<>();

        int minDiff = Integer.MAX_VALUE;
        int maxStock = 0;

        for (InventorySegmentModel segment : segments) {
            int available = segment.getAvailableStock();
            if (available < quantity) {
                continue;
            }

            if (available == quantity) {
                exactMatches.add(segment);
                continue;
            }

            int diff = available - quantity;
            if (diff < minDiff) {
                closeMatches.clear();
                closeMatches.add(segment);
                minDiff = diff;
            } else if (diff == minDiff) {
                closeMatches.add(segment);
            }

            if (available > maxStock) {
                largestMatches.clear();
                largestMatches.add(segment);
                maxStock = available;
            } else if (available == maxStock) {
                largestMatches.add(segment);
            }
        }

        Random random = new Random();
        if (!exactMatches.isEmpty()) {
            return exactMatches.get(random.nextInt(exactMatches.size()));
        }
        
        if (!closeMatches.isEmpty()) {
            return closeMatches.get(random.nextInt(closeMatches.size()));
        }
        
        if (!largestMatches.isEmpty()) {
            return largestMatches.get(random.nextInt(largestMatches.size()));
        }

        return null;
    }
} 