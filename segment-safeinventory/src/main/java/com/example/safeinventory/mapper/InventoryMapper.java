package com.example.safeinventory.mapper;


import com.example.safeinventory.model.InventoryModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryMapper {

    int insertInventory(InventoryModel inventory);

    InventoryModel selectByProductId(@Param("productId") Integer productId);

    InventoryModel selectByProductIdForUpdate(@Param("productId") Integer productId);

}

