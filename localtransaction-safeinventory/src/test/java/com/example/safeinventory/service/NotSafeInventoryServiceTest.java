package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;

@SpringBootTest
class NotSafeInventoryServiceTest {


    @Autowired
    NotSafeInventoryService notSafeInventoryService;

    @Test
    void reduceInventory() {

        //notSafeInventoryService.reduceInventory(1, 1);

        ExecutorService executorService = Executors.newFixedThreadPool(10); // 创建线程池

        // 启动 100 个线程并发调用 targetMethod
        for (int i = 0; i < 100; i++) {
            executorService.submit(() -> {
                notSafeInventoryService.reduceInventory(1, 1);
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