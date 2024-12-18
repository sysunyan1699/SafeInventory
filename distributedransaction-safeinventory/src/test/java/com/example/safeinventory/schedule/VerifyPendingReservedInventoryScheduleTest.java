package com.example.safeinventory.schedule;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VerifyPendingReservedInventoryScheduleTest {

    @Autowired
    VerifyPendingReservedInventorySchedule verifyPendingReservedInventory;

    @Test
    void verifyScheduledTask() {
        verifyPendingReservedInventory.verifyScheduledTask();
    }
}