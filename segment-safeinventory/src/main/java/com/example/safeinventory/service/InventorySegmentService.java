package com.example.safeinventory.service;


import com.example.safeinventory.mapper.InventoryMapper;
import com.example.safeinventory.mapper.InventorySegmentMapper;
import com.example.safeinventory.model.InventoryModel;
import com.example.safeinventory.model.InventorySegmentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventorySegmentService {
    private static final Logger logger = LoggerFactory.getLogger(InventorySegmentService.class);

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventorySegmentMapper inventorySegmentMapper;

    @Autowired
    RedisDistributedLock redisDistributedLock;

    private static final int SEGMENT_STOCK = 20;

    private static final String CURRENT_SEGMENT_POINTER = "current_segment_pointer";


    /**
     * 处理库存扣减请求，轮询选择库存分段，直到扣减成功或库存不足
     */

    public boolean reduceInventoryV1(int productId, int quantity) {

        String lockKey = CURRENT_SEGMENT_POINTER + ":" + productId;

        String segmentStr = redisDistributedLock.get(lockKey);
        Integer segmentId;

        // 从数据库查询当前分段
        if (segmentStr == null) {
            segmentId = initializePointer(productId);
        } else {
            segmentId = Integer.parseInt(segmentStr);
        }

        if (segmentId == 0) {
            return false;
        }

        boolean isSuccess = false;
        //todo  总段数待实现
        int totalSegment = 5;
        int runSegmentId = segmentId;

        while (!isSuccess && runSegmentId <= totalSegment) {
            if (doReduceInventoryInSegmentV1(productId, runSegmentId, quantity)) {

                return true;
            } else {
                runSegmentId++;
            }

        }


        return true;
    }

    @Transactional
    public boolean doReduceInventoryInSegmentV1(int productId, int segmentId, int quantity) {
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

        // 更新segmentPointer
        // todo 更新的值不能超过总段数
        if (segment.getAvailableStock() == quantity) {
            updatePointer(productId, segment.getVersion(), segmentId + 1);

        }

        return result == 1;
    }


    /**
     * 随机选择库存段扣减
     *
     * @param productId
     * @param quantity
     * @return
     */
    public boolean reduceInventoryV2(int productId, int quantity) {

        // 获取该商品的所有库存分段
        List<InventorySegmentModel> segments = inventorySegmentMapper.getSegmentsByProductId(productId);
        if (segments == null || segments.isEmpty()) {
            return false; // 没有库存分段，返回库存不足
        }

        // 动态轮询索引，基于当前库存分段数量
        int segmentCount = segments.size();
        Random random = new Random();
        int startIndex = random.nextInt(segmentCount); // 使用随机数或其他方式选择起始索引
        int attempt = 0;

        // 尝试扣减库存，轮询所有库存段
        while (attempt < segmentCount) {
            InventorySegmentModel segment = segments.get(startIndex);
            // 尝试扣减库存
            boolean success = doReduceInventoryInSegment(productId, segment.getSegmentId(), quantity);

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
    public boolean doReduceInventoryInSegment(int productId, int segmentId, int quantity) {
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
        inventory.setAvailableStock(totalStock);
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


    /**
     * 从数据库初始化指针并设置到 Redis
     */
    public int initializePointer(int productId) {
        InventoryModel inventoryModel = inventoryMapper.selectByProductId(productId);
        if (inventoryModel == null) {
            return 0;
        }
        redisDistributedLock.set(CURRENT_SEGMENT_POINTER + ":" + productId,
                String.valueOf(inventoryModel.getCurrentSegmentPointer()));

        return inventoryModel.getCurrentSegmentPointer();
    }


    /**
     * 更新当前分段指针到 Redis 和数据库
     */
    public void updatePointer(int productId, int version, int newPointer) {
        // 同步更新数据库
        int dbResult = inventoryMapper.updateSegmentPointer(productId, version, newPointer);

        if (dbResult != 1) {

        }
        // 更新 Redis
        redisDistributedLock.set(CURRENT_SEGMENT_POINTER + ":" + productId, String.valueOf(newPointer));
    }
}
