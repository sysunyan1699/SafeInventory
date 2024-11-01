package com.example.safeinventory.service;


import com.example.safeinventory.mapper.InventoryMapper;
import com.example.safeinventory.mapper.InventorySegmentMapper;
import com.example.safeinventory.model.InventoryModel;
import com.example.safeinventory.model.InventorySegmentModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventorySegmentService {
    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventorySegmentMapper inventorySegmentMapper;

    private static final int SEGMENT_STOCK = 5;

    // 当前轮询分段的索引
    private AtomicInteger currentIndex = new AtomicInteger(0);

    /**
     * 处理库存扣减请求，轮询选择库存分段，直到扣减成功或库存不足
     */
    public boolean reduceInventory(int productId, int quantity) {
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
//
//        int segmentCount = segments.size();
//        int startIndex = currentIndex.getAndUpdate(i -> (i + 1) % segmentCount); // 获取并更新当前索引
//        int attempt = 0;

        // 尝试扣减库存，轮询所有库存段
        while (attempt < segmentCount) {
            InventorySegmentModel segment = segments.get(startIndex);

            // 尝试扣减库存
            boolean success = doReduceInventory(productId, quantity, segment.getSegmentId());

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
    public boolean doReduceInventory(int productId, int quantity, int segmentId) {
        // 获取该商品的所有库存分段
        InventorySegmentModel segment = inventorySegmentMapper.getSegmentForUpdate(productId, segmentId);


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
}
