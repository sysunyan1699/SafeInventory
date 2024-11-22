package com.example.safeinventory.mapper;


import com.example.safeinventory.model.InventoryModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryMapper {
    InventoryModel selectByProductId(@Param("productId") Integer productId);

    InventoryModel selectByProductIdForUpdate(@Param("productId") Integer productId);

    int reserveStock(@Param("productId") Integer productId, @Param("quantity") Integer quantity);


    int reserveStockByCheckingStock(@Param("productId") Integer productId,
                                    @Param("quantity") Integer quantity,
                                    @Param("quantityAtLeast") Integer quantityAtLeast);

    int reserveStockWithVersion(@Param("productId") Integer productId,
                                @Param("quantity") Integer quantity,
                                @Param("version") Integer version);


    int confirmStock(@Param("productId") Integer productId,
                     @Param("reservedStock") Integer reservedStock);


    int rollbackStock(@Param("productId") Integer productId,
                      @Param("reservedStock") Integer reservedStock);


}

