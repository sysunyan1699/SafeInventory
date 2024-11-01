package com.example.safeinventory.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


@Service
public class RedisDistributedLock {

    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLock.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private Jedis jedis;


    // 构造函数初始化 Jedis 连接
    public RedisDistributedLock() {
        // 在构造函数中初始化 Jedis
        this.jedis = new Jedis("localhost", 6379);
    }

    /**
     * 尝试获取锁
     *
     * @return true 如果成功获取锁，false 如果获取失败
     */
    public boolean acquireLock(String lockKey, String lockValue, long expireTime) {

        try {
            logger.info("acquireLock，key:{}， value:{}, expireTime:{}", lockKey, lockValue, expireTime);

            // Lua 脚本，使用 SETNX 和 EXPIRE 实现分布式锁
            String luaScript = "if redis.call('setnx', KEYS[1], ARGV[1]) == 1 then" +
                    "    redis.call('expire', KEYS[1], ARGV[2])" +
                    "    return 1 " +
                    "else" +
                    "    return 0 " +
                    "end";

            List<String> keys = new ArrayList<>();
            List<String> values = new ArrayList<>();
            keys.add(lockKey);
            values.add(lockValue);
            values.add(String.valueOf(expireTime));

            Object result = jedis.eval(luaScript, keys, values);
            return result.equals(1L);

        } catch (Exception e) {
            logger.error("acquireLock，key:{}， value:{}, expireTime:{},error:{}", lockKey, lockValue, expireTime, e);
            throw new RuntimeException("acquireLock error", e);
        }
    }

    /**
     * 释放锁
     *
     * @return true 如果成功释放锁，false 如果释放失败
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        try {
            logger.info("releaseLock，key:{}， value:{}", lockKey, lockValue);

            // Lua 脚本，保证原子性：只有持有锁的客户端才能释放锁
            String luaScript =
                    "if redis.call('get', KEYS[1]) == false then " +
                            "    return 1 " +
                            "elseif redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "    return redis.call('del', KEYS[1]) " +
                            "else " +
                            "    return 2 " +
                            "end";


            List<String> keys = Collections.singletonList(lockKey);
            List<String> values = Collections.singletonList(lockValue);

            Object result = jedis.eval(luaScript, keys, values);

            return result.equals(1L);
        } catch (Exception e) {
            logger.error("releaseLock，key:{}， value:{}, error:{}", lockKey, lockValue, e);
            throw new RuntimeException("releaseLock error");
        }
    }

    /**
     * 获取当前锁的值
     *
     * @return 锁的当前值，如果没有被锁定则返回 null
     */
    public String get(String lockKey) {
        return jedis.get(lockKey);
    }


    public void extendLock(String lockKey, String lockValue, long additionalTime) {
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('expire', KEYS[1], ARGV[2]) " +
                "else " +
                "    return 0 " +
                "end";
        jedis.eval(luaScript, Collections.singletonList(lockKey),
                Arrays.asList(lockValue, String.valueOf(additionalTime)));
    }


    public boolean reduceStock(String key, Integer requestQuality) {
        String luaScript =
                "local current_stock = tonumber(redis.call('GET', KEYS[1])) " +
                        "local deduct_amount = tonumber(ARGV[1]) " +
                        "if current_stock == nil then " +
                        "    return -1 " +
                        "elseif current_stock < deduct_amount then " +
                        "    return 0 " +
                        "else " +
                        "    redis.call('DECRBY', KEYS[1], deduct_amount) " +
                        "    return 1 " +
                        "end";

        List<String> keys = Collections.singletonList(key);
        List<String> values = Collections.singletonList(requestQuality.toString());

        // 执行 Lua 脚本
        Object result = jedis.eval(luaScript, keys, values);

        if (result.equals(1L)) {
            System.out.println("扣减成功");
            return true;
        } else if (result.equals(0L)) {
            System.out.println("库存不足");

        } else if (result.equals(-1L)) {
            System.out.println("库存未初始化或不存在");
        }

       return true;
    }

}
