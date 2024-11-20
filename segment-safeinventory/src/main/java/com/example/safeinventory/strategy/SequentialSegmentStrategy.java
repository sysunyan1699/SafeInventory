package com.example.safeinventory.strategy;

import com.example.safeinventory.model.InventorySegmentModel;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 顺序选择策略实现
 */
@Component
public class SequentialSegmentStrategy implements SegmentSelectionStrategy {
    @Override
    public InventorySegmentModel selectSegment(List<InventorySegmentModel> segments, int quantity) {
        return segments.stream()
                .filter(segment -> segment.getAvailableStock() >= quantity)
                .findFirst()
                .orElse(null);
    }
} 