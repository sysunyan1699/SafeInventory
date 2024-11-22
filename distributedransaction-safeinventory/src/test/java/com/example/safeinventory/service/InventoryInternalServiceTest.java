package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;


@SpringBootTest
class InventoryInternalServiceTest {

    @Autowired
    InventoryInternalService inventoryInternalService;

    @Test
    void reserveInventory() {
        UUID uuid = UUID.randomUUID();
        inventoryInternalService.reserveInventory(1, 1, uuid.toString());
    }

    @Test
    void confirmReservedInventory() {

        inventoryInternalService.confirmReservedInventory(1,
                "8a88b7f5-8d17-4f8c-b3d2-6d4961d10ad3",
                1,
                1);
    }

    @Test
    void rollbackReservedInventory() {

        inventoryInternalService.rollbackReservedInventory(1,
                "76b09e6c-0a5d-44b4-af92-32f96567104d");
    }
}