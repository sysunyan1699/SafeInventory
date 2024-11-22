package com.example.safeinventory.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InventoryMapperTest {

    @Autowired
    InventoryMapper inventoryMapper;

    @Test
    void selectByProductId() {

    }

    @Test
    void selectByProductIdForUpdate() {
    }

    @Test
    void reserveStock() {
    }

    @Test
    void reserveStockByCheckingStock() {
    }

    @Test
    void reserveStockWithVersion() {
    }

    @Test
    void confirmStock() {
    }

    @Test
    void rollbackStock() {
    }
}