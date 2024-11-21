package com.example.safeinventory.strategy;

import com.example.safeinventory.model.InventorySegmentModel;
import java.util.List;

/**
 * 库存分段合并检查策略接口
 */
public interface MergeCheckStrategy {
    /**
     * 检查是否需要合并
     * @param segments 当前有效的库存分段列表
     * @return 是否需要进行合并
     */
    boolean isNeedMerge(List<InventorySegmentModel> segments);
} 