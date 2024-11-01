package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
class InventorySegmentWithRedisServiceTest {

    @Autowired
    InventorySegmentWithRedisService inventorySegmentWithRedisService;

//    @Test
//    void reduceInventory() {
//        inventorySegmentWithRedisService.reduceInventory(4, 1, "");
//    }


    @Test
    void reduceInventory() {
        ExecutorService executorService = Executors.newFixedThreadPool(10); // 创建线程池
        for (int i = 0; i < 100; i++) {
            //executorService.submit(() -> {
                inventorySegmentWithRedisService.reduceInventory(4, 1,"");
            //});
        }
       // executorService.shutdown(); // 停止接受新任务
        try {
            Thread.sleep(10000);
//            // 等待所有任务完成
//            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
//                executorService.shutdownNow(); // 如果超时则强制停止
//            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}