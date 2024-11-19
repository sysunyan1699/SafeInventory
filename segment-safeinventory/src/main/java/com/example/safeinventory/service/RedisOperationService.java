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
public class RedisOperationService {

    private static final Logger logger = LoggerFactory.getLogger(RedisOperationService.class);

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private Jedis jedis;

    public RedisOperationService() {
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
            logger.error("acquireLock，key:{}， value:{}, expireTime:{},error:{}", lockKey, lockValue, expireTime, e.getMessage());
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
            String luaScript = "if redis.call('get', KEYS[1]) == false then" +
                    "    return 1 " +
                    "elseif redis.call('get', KEYS[1]) == ARGV[1] then" +
                    "    return redis.call('del', KEYS[1])" +
                    "else" +
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

    public void set(String lockKey, String lockValue){
        jedis.set(lockKey, lockValue);
    }

    /**
     * 锁续期
     *
     * @param lockKey
     * @param lockValue
     * @param additionalTime
     */
    public void extendLock(String lockKey, String lockValue, long additionalTime) {
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('expire', KEYS[1], ARGV[2]) " +
                "else " +
                "    return 0 " +
                "end";
        jedis.eval(luaScript, Collections.singletonList(lockKey),
                Arrays.asList(lockValue, String.valueOf(additionalTime)));
    }

    public long reduceStock(String key, Integer requestQuality) {
        String luaScript =
                "local current_stock = tonumber(redis.call('GET', KEYS[1])) " +
                        "local deduct_amount = tonumber(ARGV[1]) " +
                        "if current_stock == nil then " +
                        "    return -2 " +
                        "elseif current_stock == 0 then " +
                        "    return 0 " +
                        "elseif current_stock < deduct_amount then " +
                        "    return -1 " +
                        "else " +
                        "    redis.call('DECRBY', KEYS[1], deduct_amount) " +
                        "    return 1 " +
                        "end";

        List<String> keys = Collections.singletonList(key);
        List<String> values = Collections.singletonList(requestQuality.toString());

        return (long) jedis.eval(luaScript, keys, values);
    }

    /**
     * 将某个库存分段中扣减的库存量加回去
     */
    public void rollbackInventory(String productKey, String segmentId, int quantity) {
        // 将之前扣减的库存量加回去
        jedis.hincrBy(productKey, segmentId, quantity);
    }

    public long reduceStock(String redisKey, String segmentId, int quantity) {
        // Lua 脚本，使用 HGET 获取库存，并根据情况扣减
        String luaScript = "local stock = tonumber(redis.call('hget', KEYS[1], ARGV[1])) " +
                "if stock >= tonumber(ARGV[2]) then " +
                "   local newStock = stock - tonumber(ARGV[2]) " +
                "   if newStock == 0 then " +
                "       redis.call('hdel', KEYS[1], ARGV[1]) " +  // 如果库存为 0，删除该分段
                "   else " +
                "       redis.call('hset', KEYS[1], ARGV[1], newStock) " +  // 更新库存
                "   end " +
                "   return 1 " +
                "else " +
                "   return -1 " +
                "end";

        // 执行Lua脚本，传递参数 KEYS 和 ARGV
        Object result = jedis.eval(luaScript,
                1,          // KEYS 数量
                redisKey,   // KEYS[1]
                segmentId,  // ARGV[1]，对应的是分段的 segment_id
                String.valueOf(quantity));  // ARGV[2]，扣减的库存量

        // 根据Lua脚本的返回结果判断扣减是否成功
        return (long) result;
    }

    public long getInventorySegmentCount(String productKey) {
        return jedis.hlen(productKey); // 获取哈希表中字段的数量，即库存分段数量
    }

    public List<String> getInventorySegmentIds(String productKey) {
        return jedis.hkeys(productKey).stream().toList();
    }

    /**
     * 获取Hash中的字段值
     * @param key Hash的key
     * @param field Hash的字段名
     * @return 字段值，如果不存在返回null
     */
    public String hget(String key, String field) {
        try {
            return jedis.hget(key, field);
        } catch (Exception e) {
            logger.error("获取Hash字段失败 key:{}, field:{}, error:{}", key, field, e.getMessage());
            return null;
        }
    }

    /**
     * 设置Hash中的字段值
     * @param key Hash的key
     * @param field Hash的字段名
     * @param value 要设置的值
     */
    public void hset(String key, String field, String value) {
        try {
            jedis.hset(key, field, value);
        } catch (Exception e) {
            logger.error("设置Hash字段失败 key:{}, field:{}, value:{}, error:{}", 
                key, field, value, e.getMessage());
            throw new RuntimeException("设置Hash字段失败", e);
        }
    }

    /**
     * 执行Lua脚本
     * @param script Lua脚本内容
     * @param keys KEYS参数列表
     * @param args ARGV参数列表
     * @return 脚本执行结果
     */
    public Object evalScript(String script, List<String> keys, List<String> args) {
        try {
            return jedis.eval(script, keys, args);
        } catch (Exception e) {
            logger.error("执行Lua脚本失败 script:{}, keys:{}, args:{}, error:{}", 
                script, keys, args, e.getMessage());
            throw new RuntimeException("执行Lua脚本失败", e);
        }
    }

}
