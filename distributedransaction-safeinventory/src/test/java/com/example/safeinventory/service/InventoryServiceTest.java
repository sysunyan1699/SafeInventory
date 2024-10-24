package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class InventoryServiceTest {

    @Autowired
    InventoryService inventoryService;


    @Test
    void reserveInventory() {
        UUID uuid = UUID.randomUUID();
        inventoryService.reserveInventory(1, 1, uuid.toString());
    }

    @Test
    void confirmReservedInventory() {
    }

    @Test
    void confirmReservedInventoryWithBusinessLogic() {
    }

    @Test
    void rollbackReservedInventory() {
    }
}