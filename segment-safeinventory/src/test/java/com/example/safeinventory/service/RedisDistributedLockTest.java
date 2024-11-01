package com.example.safeinventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisDistributedLockTest {

    @Autowired
    RedisDistributedLock redisDistributedLock;

    @Test
    void acquireLock() {
        assertEquals(true, redisDistributedLock.acquireLock("key1", "value1", 600));

        assertEquals(true, redisDistributedLock.acquireLock("key1", "value1", 600));

    }

    @Test
    void releaseLock() {
        assertEquals(false, redisDistributedLock.releaseLock("key1", "value2"));
        assertEquals(true, redisDistributedLock.releaseLock("key1", "value1"));


    }

    @Test
    void get() {
    }

    @Test
    void extendLock() {
        redisDistributedLock.extendLock("key1", "value1", 600);

    }

    @Test
    void reduceStock() {
        assertEquals(1, redisDistributedLock.reduceStock("product_stock:1", 2));
        assertEquals(1, redisDistributedLock.reduceStock("product_stock:1", 7));
        assertEquals(-1, redisDistributedLock.reduceStock("product_stock:1", 7));



    }

    @Test
    void rollbackStock() {
        //redisDistributedLock.rollbackStock("product_stock:1", 9);
    }
}