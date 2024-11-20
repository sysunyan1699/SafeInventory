package com.example.safeinventory.mapper;


import com.example.safeinventory.model.InventorySegmentModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventorySegmentMapper {

    // 批量插入 InventorySegment
    int batchInsert(@Param("segments") List<InventorySegmentModel> segments);

    // 根据商品ID获取所有库存分段，并且只返回有可用库存的分段
    List<InventorySegmentModel> getSegmentsByProductId(@Param("productId") int productId);

    InventorySegmentModel getSegmentForUpdate(@Param("productId") int productId,
                                              @Param("segmentId") int segmentId);

    int reduceAvailableStockWithVersion(@Param("productId") int productId,
                                        @Param("segmentId") int segmentId,
                                        @Param("quantity") int quantity,
                                        @Param("version") int version);

    /**
     * 将指定商品的所有分段标记为无效
     */
    int invalidateSegments(@Param("productId") int productId);

    /**
     * 获取商品的有效分段
     */
    List<InventorySegmentModel> getValidSegmentsByProductId(@Param("productId") int productId);

    /**
     * 获取指定商品的最大分段ID
     */
    Integer getMaxSegmentId(@Param("productId") int productId);

    /**
     * 获取所有有效的库存分段
     */
    List<InventorySegmentModel> getAllValidSegments();
}
