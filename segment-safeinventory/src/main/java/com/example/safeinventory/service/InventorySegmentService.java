package com.example.safeinventory.service;


import com.example.safeinventory.mapper.InventoryMapper;
import com.example.safeinventory.mapper.InventorySegmentMapper;
import com.example.safeinventory.model.ActiveSegmentInfo;
import com.example.safeinventory.model.InventoryModel;
import com.example.safeinventory.model.InventorySegmentModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class InventorySegmentService {
    private static final Logger logger = LoggerFactory.getLogger(InventorySegmentService.class);

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventorySegmentMapper inventorySegmentMapper;

    @Autowired
    RedisOperationService redisOperationService;


    private static final int SEGMENT_STOCK = 4;

    private static final int SEGMENT_INFO_EXPIRE_TIME = 24 * 60 * 60;

    private static int ALL_STOCK_HAS_REDUCED = -1;

    /**
     * Redis中存储分段信息的hash key前缀
     * Hash结构包含:
     * - pointer: 当前使用的分段ID，-1表示所有分段已耗尽
     * - count: 该商品的分段总数
     * - version: 版本号
     * 例如: "activeSegmentInfo:1001" = {
     * pointer: "-1",  // 表示已耗尽，从1开始的值表示当前活跃段
     * count: "5",
     * version: "1732002726000"
     * }
     */
    private static final String SEGMENT_INFO_KEY = "activeSegmentInfo:";

    /**
     * 固定库存扣减，使用顺序分段策略
     */
    public boolean reduceFixedInventory(int productId, int quantity) {
        // 1. 验证扣减量是否合法
        if (quantity > SEGMENT_STOCK) {
            logger.warn("扣减量{}大于分段库存容量{}", quantity, SEGMENT_STOCK);
            return false;
        }

        // 2. 获取当前活跃分段信息
        ActiveSegmentInfo segmentInfo = getCurrentSegmentInfo(productId);
        if (segmentInfo == null || segmentInfo.getCurrentPointer() == ALL_STOCK_HAS_REDUCED) {
            logger.warn("无可用库存分段信息 productId:{}", productId);
            return false;
        }

        // 3. 在当前分段尝试扣减
        boolean success = doReduceInventoryInSegment(productId, segmentInfo, quantity);
        if (success) {
            return true;
        }

        // 4. 当前分段扣减失败，尝试移动到下一个可用分段
        return tryMoveToNextSegment(productId, quantity, segmentInfo);
    }

    /**
     * 获取当前分段信息
     */
    private ActiveSegmentInfo getCurrentSegmentInfo(int productId) {

        ActiveSegmentInfo segmentInfo = getSegmentInfoFromRedis(productId);

        if (segmentInfo != null) {
            return segmentInfo;
        }
        return initializeSegmentInfo(productId);
    }

    /**
     * 尝试移动到下一个可用分段并扣减库存
     */
    @Transactional
    public boolean tryMoveToNextSegment(int productId, int quantity, ActiveSegmentInfo segmentInfo) {
        // 1. 遍历后续分段
        for (int nextPointer = segmentInfo.getCurrentPointer() + 1;
             nextPointer <= segmentInfo.getTotalSegments();
             nextPointer++) {

            // 2. ���证分段是否有足够库存
            InventorySegmentModel segment = inventorySegmentMapper.getSegmentForUpdate(productId, nextPointer);
            if (segment == null || segment.getAvailableStock() < quantity) {
                continue;
            }

            segmentInfo.setCurrentPointer(nextPointer);
            // 3. 尝试在新分段扣减
            if (doReduceInventoryInSegment(productId, segmentInfo, quantity)) {
                // 6. 更新指针
                updateSegmentPointerWithVersion(productId, nextPointer);
                return true;
            }
        }

        updateSegmentPointerWithVersion(productId, ALL_STOCK_HAS_REDUCED);
        logger.warn("所有分段库存都已耗尽或不足 productId:{}, quantity:{}", productId, quantity);
        return false;
    }

    /**
     * 更新分段指针，使用Hash结构和版本号控制并发
     */
    private boolean updateSegmentPointerWithVersion(int productId, int newPointer) {
        String infoKey = SEGMENT_INFO_KEY + productId;

        // 使用Lua脚本保证原子性
        String luaScript =
                "local currentVersion = tonumber(redis.call('hget', KEYS[1], 'version') or '0') " +
                        "local newVersion = tonumber(ARGV[2]) " +
                        "if newVersion > currentVersion then " +
                        "    redis.call('hmset', KEYS[1], " +
                        "        'pointer', ARGV[1], " +
                        "        'version', newVersion) " +
                        "    redis.call('expire', KEYS[1], ARGV[3]) " +
                        "    return 1 " +  // 更新成功
                        "else " +
                        "    return 0 " +  // 版本号不符，更新失败
                        "end";


        try {
            List<String> keys = List.of(infoKey);
            List<String> args = List.of(
                    String.valueOf(newPointer),           // ARGV[1]: 新的指针值
                    String.valueOf(System.currentTimeMillis()),  // ARGV[2]: 新版本号(使用时间戳)
                    String.valueOf(SEGMENT_INFO_EXPIRE_TIME)          // ARGV[3]: 过期时间
            );

            Long result = (Long) redisOperationService.evalScript(luaScript, keys, args);
            boolean success = result == 1;

            if (success) {
                logger.info("更新分段指针成功 productId:{}, newPointer:{}, newVersion:{}",
                        productId, newPointer, args.get(1));
            } else {
                logger.warn("更新分段指针失败，版本号过期 productId:{}, newPointer:{}",
                        productId, newPointer);
            }

            return success;
        } catch (Exception e) {
            logger.error("更新分段指针异常 productId:{}, newPointer:{}, error:{}",
                    productId, newPointer, e.getMessage());
            return false;
        }
    }

    /**
     * 初始化redis中分段信息
     * 使用双重检查锁定模式(Double-Checked Locking)
     */
    private ActiveSegmentInfo initializeSegmentInfo(int productId) {
        String infoKey = SEGMENT_INFO_KEY + productId;
        String lockKey = "lock:" + infoKey;
        boolean lockAcquired = false;

        try {
            // 1. 再次检查Redis中是否已有数据（其他线程可能已经初始化完成）
            ActiveSegmentInfo segmentInfo = getSegmentInfoFromRedis(productId);
            if (segmentInfo != null) {
                return segmentInfo;
            }

            // 2. 尝试获取分布式锁
            lockAcquired = redisOperationService.acquireLock(lockKey, String.valueOf(productId), 10000);
            if (!lockAcquired) {
                // 未获取到锁，等待一段��间后重试获取Redis��据
                Thread.sleep(100);
                return getSegmentInfoFromRedis(productId);
            }

            // 3. 获取锁后再次检查Redis（双重检查）
            segmentInfo = getSegmentInfoFromRedis(productId);
            if (segmentInfo != null) {
                return segmentInfo;
            }

            // 4. 从数据库加载数据
            List<InventorySegmentModel> segments = inventorySegmentMapper.getSegmentsByProductId(productId);
            if (segments.isEmpty()) {
                return null;
            }

            // 5. 初始化Redis数据
            int pointer = segments.get(0).getSegmentId();
            int count = segments.size();

            String luaScript =
                    "redis.call('hmset', KEYS[1], " +
                            "   'pointer', ARGV[1], " +
                            "   'count', ARGV[2], " +
                            "   'version', ARGV[3]) " +
                            "redis.call('expire', KEYS[1], ARGV[4]) " +
                            "return 1";

            List<String> keys = List.of(infoKey);
            long version = System.currentTimeMillis();
            List<String> args = List.of(
                    String.valueOf(pointer),
                    String.valueOf(count),
                    String.valueOf(version),
                    String.valueOf(SEGMENT_INFO_EXPIRE_TIME)
            );

            Long result = (Long) redisOperationService.evalScript(luaScript, keys, args);

            if (result == 1) {
                logger.info("初始化分段信息成功 productId:{}, pointer:{}, count:{}",
                        productId, pointer, count);
                return new ActiveSegmentInfo(pointer, count, version);
            } else {
                logger.error("初始化分段信息失败 productId:{}", productId);
                return null;
            }

        } catch (Exception e) {
            logger.error("初始化分段信息失败 productId:{}, error:{}", productId, e.getMessage());
            return null;
        } finally {
            if (lockAcquired) {
                redisOperationService.releaseLock(lockKey, String.valueOf(productId));
            }
        }
    }

    /**
     * 从Redis获取分段信息
     */
    private ActiveSegmentInfo getSegmentInfoFromRedis(int productId) {
        String infoKey = SEGMENT_INFO_KEY + productId;

        // 使用Lua脚本原子获取所有信息
        String luaScript =
                "return {" +
                        "  redis.call('hget', KEYS[1], 'pointer')," +   // pointer
                        "  redis.call('hget', KEYS[1], 'count')," +     // count
                        "  redis.call('hget', KEYS[1], 'version')" +    // version
                        "}";

        List<String> keys = Arrays.asList(infoKey);
        List<String> result = (List<String>) redisOperationService.evalScript(luaScript, keys, Collections.emptyList());

        if (result.get(0) == null || result.get(1) == null) {
            return null;
        }

        return new ActiveSegmentInfo(
                Integer.parseInt(result.get(0)),  // pointer
                Integer.parseInt(result.get(1)),  // count
                result.get(2) == null ? 0 : Long.parseLong(result.get(2))  // version
        );
    }

    @Transactional
    public boolean doReduceInventoryInSegment(int productId, ActiveSegmentInfo segmentInfo, int quantity) {
        int segmentId = segmentInfo.getCurrentPointer();
        logger.info("productId: {}, segmentId:{}, quantity: {}，", productId, segmentId, quantity);

        InventorySegmentModel segment = inventorySegmentMapper.getSegmentForUpdate(productId, segmentId);
        if (segment == null) {
            logger.warn("库存分段不存在 productId: {}, segmentId:{},quantity: {}，", productId, segmentId, quantity);
            return false;
        }
        if (segment.getAvailableStock() < quantity) {
            logger.info("库存不足 productId: {}, segmentId:{},quantity: {}，", productId, segmentId, quantity);
            return false;
        }
        // 尝试扣减库存
        int result = inventorySegmentMapper.reduceAvailableStockWithVersion(
                segment.getProductId(),
                segment.getSegmentId(),
                quantity,
                segment.getVersion()
        );

        // 更新segmentPointer
        if (result != 1) {
            return false;
        }

        //  如果当前分段刚好用完，则更新指针到下一个分段
        if (segment.getAvailableStock() == quantity) {
            if (segmentId + 1 > segmentInfo.getTotalSegments()) {
                updateSegmentPointerWithVersion(productId, ALL_STOCK_HAS_REDUCED);
            } else {
                updateSegmentPointerWithVersion(productId, segmentId + 1);
            }
        }
        return true;
    }


    /**
     * 随机选择库存段扣减
     *
     * @param productId 商品ID
     * @param quantity  扣减数量
     * @return 扣减是否成功
     */
    public boolean reduceRandomInventory(int productId, int quantity) {
        // 获取该商品的所有库存分段
        List<InventorySegmentModel> segments = inventorySegmentMapper.getSegmentsByProductId(productId);
        if (segments == null || segments.isEmpty()) {
            return false; // 没有库存分段，返回存不足
        }

        // 动态轮询索引，基于当前库存分段数量
        int segmentCount = segments.size();
        Random random = new Random();
        int startIndex = random.nextInt(segmentCount); // 使用随机数选择起始索引
        int attempt = 0;

        // 尝试扣减库存，轮询所有库存段
        while (attempt < segmentCount) {
            InventorySegmentModel segment = segments.get(startIndex);
            // 尝试扣减库存
            boolean success = doReduceInventoryInSegmentV2(productId, segment.getSegmentId(), quantity);

            if (success) {
                return true; // 扣减成功
            }
            // 如果失败，尝试下一个分段
            startIndex = (startIndex + 1) % segmentCount;
            attempt++;
        }

        // 所有分段扣减失败，返回库存不足
        return false;
    }

    @Transactional
    public boolean doReduceInventoryInSegmentV2(int productId, int segmentId, int quantity) {
        logger.info("productId: {}, segmentId:{},quantity: {}，", productId, segmentId, quantity);
        InventorySegmentModel segment = inventorySegmentMapper.getSegmentForUpdate(productId, segmentId);
        if (segment.getAvailableStock() < quantity) {
            logger.info("库存不足 productId: {}, segmentId:{},quantity: {}，", productId, segmentId, quantity);
            return false;
        }
        // 尝试扣减库存
        int result = inventorySegmentMapper.reduceAvailableStockWithVersion(
                segment.getProductId(),
                segment.getSegmentId(),
                quantity,
                segment.getVersion()
        );
        return result == 1;
    }


    @Transactional
    public void createInventoryWithSegments(int productId, int totalStock) {
        // 计算分段数量
        int segmentCount = (int) Math.ceil((double) totalStock / SEGMENT_STOCK);

        // 插入主库存记录
        InventoryModel inventory = new InventoryModel();
        inventory.setProductId(productId);
        inventory.setTotalStock(totalStock);
        inventoryMapper.insertInventory(inventory); // 保存主库存记录

        // 准备插入分段记录
        List<InventorySegmentModel> segments = new ArrayList<>();
        for (int i = 1; i <= segmentCount; i++) {
            int stockForSegment = Math.min(SEGMENT_STOCK, totalStock); // 最后一段可能不足segmentStock
            totalStock -= stockForSegment; // 减去已分配给段的库存
            InventorySegmentModel segment = new InventorySegmentModel();
            segment.setProductId(productId);
            segment.setSegmentId(i);
            segment.setTotalStock(stockForSegment);
            segment.setAvailableStock(stockForSegment);
            segments.add(segment);
        }
        // 插入所有分段库存
        inventorySegmentMapper.batchInsert(segments);
    }

    private static final String PREWARM_LOCK_KEY = "inventory:prewarm:lock";
    private static final int PREWARM_LOCK_TIMEOUT = 300;  // 预热锁超时时间，单位秒
    private static final String PREWARM_STATUS_KEY = "inventory:prewarm:status";

    @PostConstruct
    public void initializeService() {
        if (!isPrewarmRequired()) {
            logger.info("预热已完成，无需重复预热");
            return;
        }
        String serviceId = generateServiceId();  // 生成唯一的服务标识
        boolean lockAcquired = false;
        try {
            // 1. 尝试获取预热锁
            lockAcquired = redisOperationService.acquireLock(
                    PREWARM_LOCK_KEY,
                    serviceId,
                    PREWARM_LOCK_TIMEOUT * 1000
            );

            if (!lockAcquired) {
                // 2. 未获取到锁，结束
                return;
            }

            // 3. 获取到锁，执行预热
            doPrewarm();

            // 4. 标记预热完成
            markPrewarmComplete();

        } catch (Exception e) {
            logger.error("服务预热失败", e);
            throw new RuntimeException("服务预热失败，无法启动服务", e);
        } finally {
            if (lockAcquired) {
                redisOperationService.releaseLock(PREWARM_LOCK_KEY, serviceId);
            }
        }
    }

    private boolean isPrewarmRequired() {
        String status = redisOperationService.get(PREWARM_STATUS_KEY);
        return !"COMPLETED".equals(status);
    }


    private void doPrewarm() {
        logger.info("开始执行预热...");
        try {
            // todo  获取需要预热的商品列表, 需要分页查询
            List<Integer> productIds = new ArrayList<>();

            // 2. 批量预热
            for (Integer productId : productIds) {
                try {
                    initializeSegmentInfo(productId);
                    logger.info("商品{}预热成功", productId);
                } catch (Exception e) {
                    logger.error("商品{}预热失败", productId, e);
                    throw e;  // 预热失败直接抛出异常
                }
            }

            logger.info("预热完成，共预热{}个商品", productIds.size());
        } catch (Exception e) {
            logger.error("预热过程发生错误", e);
            throw new RuntimeException("预热失败", e);
        }
    }

    // todo
    private void markPrewarmComplete() {
        logger.info("预热状态已标记为完成");
    }

    private String generateServiceId() {
        return UUID.randomUUID().toString();
    }
}
