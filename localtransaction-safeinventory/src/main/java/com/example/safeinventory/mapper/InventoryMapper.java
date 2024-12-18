package com.example.safeinventory.mapper;


import com.example.safeinventory.model.InventoryModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryMapper {

    // 插入 inventory 表的数据
    int insertInventory(InventoryModel inventory);

    InventoryModel selectByProductId(@Param("productId") Integer productId);

    InventoryModel selectByProductIdForUpdate(@Param("productId") Integer productId);


    int reduceAvailableStock(@Param("productId") Integer productId,
                             @Param("quantity") Integer quantity);


    int reduceAvailableStockWithCheckingStock(@Param("productId") Integer productId,
                                              @Param("quantity") Integer quantity);

    int reduceAvailableStockWithVersion(@Param("productId") Integer productId,
                                        @Param("quantity") Integer quantity,
                                        @Param("version") Integer version);

}

