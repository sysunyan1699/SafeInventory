package com.example.safeinventory.mapper;


import com.example.safeinventory.model.InventoryModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InventoryMapper {
    //@Select("SELECT * FROM inventory WHERE product_id = #{productId}")
    InventoryModel selectByProductId(@Param("productId") Integer productId);

    //@Select("SELECT * FROM inventory WHERE product_id = #{productId} for update")
    InventoryModel selectByProductIdForUpdate(@Param("productId") Integer productId);

//    @Update("UPDATE inventory SET available_stock = available_stock - #{quantity} " +
//            "reserved_stock = reserved_stock + #{quantity}" +
//            "WHERE product_id = #{productId}")
    int reserveStock(@Param("productId") Integer productId, @Param("quantity") Integer quantity);


//    @Update("UPDATE inventory SET " +
//            "available_stock = available_stock - #{quantity}, " +
//            "reserved_stock = reserved_stock + #{quantity}" +
//            "WHERE " +
//            "product_id = #{productId} " +
//            "AND available_stock >= #{quantityAtLeast}")
    int reserveStockByCheckingStock(@Param("productId") Integer productId,
                                    @Param("quantity") Integer quantity,
                                    @Param("quantityAtLeast") Integer quantityAtLeast);

//    @Update("UPDATE inventory SET " +
//            "available_stock = available_stock - #{quantity}, " +
//            "reserved_stock = reserved_stock + #{quantity}, " +
//            "version =  version + 1 " +
//            "WHERE product_id = #{productId} and version = #{version}")
    int reserveStockWithVersion(@Param("productId") Integer productId,
                                @Param("quantity") Integer quantity,
                                @Param("version") Integer version);


//    @Update("UPDATE product_inventory" +
//            "SET reserved_stock = reserved_stock - #{reservedStock}" +
//            "WHERE product_id = #{productId}")
    int confirmStock(@Param("productId") Integer productId,
                     @Param("reservedStock") Integer reservedStock);


//    @Update("UPDATE product_inventory" +
//            "SET reserved_stock = reserved_stock + #{reservedStock}" +
//            "WHERE product_id = #{productId}")
    int rollbackStock(@Param("productId") Integer productId,
                      @Param("reservedStock") Integer reservedStock);


}

