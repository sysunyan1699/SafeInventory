package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class InventorySegmentServiceTest {

    @Autowired
    InventorySegmentService inventorySegmentService;

    @Test
    void reduceInventory() {
        inventorySegmentService.reduceInventory(4,1);
    }

    @Test
    void doReduceInventory() {
    }

    @Test
    void createInventoryWithSegments() {
    }
}