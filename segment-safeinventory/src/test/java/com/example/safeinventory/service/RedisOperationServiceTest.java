package com.example.safeinventory.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RedisOperationServiceTest {

    @Autowired
    RedisOperationService redisOperationService;

    @Test
    void acquireLock() {
        Assertions.assertEquals(true, redisOperationService.acquireLock("key1", "value1", 600));

    }

    @Test
    void releaseLock() {
        Assertions.assertEquals(false, redisOperationService.releaseLock("key1", "value2"));
        Assertions.assertEquals(true, redisOperationService.releaseLock("key1", "value1"));


    }

    @Test
    void get() {
    }

    @Test
    void extendLock() {
        redisOperationService.extendLock("key1", "value1", 600);

    }

    @Test
    void reduceStock() {
        Assertions.assertEquals(1, redisOperationService.reduceStock("product_stock:1", 2));
        Assertions.assertEquals(1, redisOperationService.reduceStock("product_stock:1", 7));
        Assertions.assertEquals(-1, redisOperationService.reduceStock("product_stock:1", 7));


    }

    @Test
    void rollbackStock() {
        //redisDistributedLock.rollbackStock("product_stock:1", 9);
    }
}