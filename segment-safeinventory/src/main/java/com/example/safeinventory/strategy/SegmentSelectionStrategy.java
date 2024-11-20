package com.example.safeinventory.strategy;

import com.example.safeinventory.model.InventorySegmentModel;
import java.util.List;

/**
 * 分段选择策略接口
 */
public interface SegmentSelectionStrategy {
    /**
     * 选择合适的库存分段
     * @param segments 可用的库存分段列表
     * @param quantity 需要扣减的数量
     * @return 选中的库存分段，如果没有合适的分段则返回null
     */
    InventorySegmentModel selectSegment(List<InventorySegmentModel> segments, int quantity);
} 