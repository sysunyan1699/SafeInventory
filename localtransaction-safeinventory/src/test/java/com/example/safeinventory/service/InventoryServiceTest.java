package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InventoryServiceTest {

    @Autowired
    InventorySegmentService inventoryService;

    @Test
    void createInventoryWithSegments() {

        inventoryService.createInventoryWithSegments(4,10);

    }
}