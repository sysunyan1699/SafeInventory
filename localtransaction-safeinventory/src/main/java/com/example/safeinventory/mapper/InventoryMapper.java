package com.example.safeinventory.mapper;


import com.example.safeinventory.model.InventoryModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryMapper {
    //@Select("SELECT * FROM inventory WHERE product_id = #{productId}")
    InventoryModel selectByProductId(@Param("productId") Integer productId);

    //@Select("SELECT * FROM inventory WHERE product_id = #{productId} for update")
    InventoryModel selectByProductIdForUpdate(@Param("productId") Integer productId);

//    @Update("UPDATE inventory SET " +
//            "available_stock = available_stock - #{quantity} " +
//            "WHERE product_id = #{productId}")
    int reduceAvailableStock(@Param("productId") Integer productId,
                             @Param("quantity") Integer quantity);


//    @Update("UPDATE inventory SET " +
//            "available_stock = available_stock - #{quantity} " +
//            "WHERE product_id = #{productId} AND available_stock >= #{quantityAtLeast}")
    int reduceAvailableStockWithCheckingStock(@Param("productId") Integer productId,
                                              @Param("quantity") Integer quantity,
                                              @Param("quantityAtLeast") Integer quantityAtLeast);


//    @Update("UPDATE inventory SET " +
//            "available_stock = available_stock - #{quantity}, " +
//            "version = version + 1 " +
//            "WHERE product_id = #{productId} AND version = #{version}")
    int reduceAvailableStockWithVersion(@Param("productId") Integer productId,
                                        @Param("quantity") Integer quantity,
                                        @Param("version") Integer version);

}

