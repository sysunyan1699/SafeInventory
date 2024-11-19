package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@SpringBootTest
class InventorySegmentServiceTest {

    @Autowired
    InventorySegmentService inventorySegmentService;

    @Test
    void createInventoryWithSegments() {
        inventorySegmentService.createInventoryWithSegments(1, 20);
    }

    @Test
    void reduceFixedInventory() {

        ExecutorService executorService = Executors.newFixedThreadPool(10); // 创建线程池


        // 启动 100 个线程并发调用 targetMethod
        for (int i = 0; i < 30; i++) {
            //inventorySegmentService.reduceFixedInventory(1, 2);
            executorService.submit(() -> {
                inventorySegmentService.reduceFixedInventory(1, 2);
            });
        }

        //executorService.shutdown(); // 停止接受新任务
        try {
            Thread.sleep(100000);

            // 等待所有任务完成
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // 如果超时则强制停止
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    @Test
    void doReduceInventory() {
    }


}