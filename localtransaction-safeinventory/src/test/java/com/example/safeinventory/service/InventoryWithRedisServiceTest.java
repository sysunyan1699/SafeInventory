package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class InventoryWithRedisServiceTest {

    @Autowired
    InventoryWithRedisService inventoryWithRedisService;


    @Test
    void reduceInventory() {

        ExecutorService executorService = Executors.newFixedThreadPool(10); // 创建线程池

        // 启动 100 个线程并发调用 targetMethod
        for (int i = 0; i < 100; i++) {
            // 生成一个UUID
            UUID uuid = UUID.randomUUID();

            executorService.submit(() -> {
                inventoryWithRedisService.reduceInventory(1, 1, uuid.toString());
            });
        }

        executorService.shutdown(); // 停止接受新任务
        try {
            // 等待所有任务完成
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // 如果超时则强制停止
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}