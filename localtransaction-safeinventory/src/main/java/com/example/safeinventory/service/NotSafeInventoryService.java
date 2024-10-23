package com.example.safeinventory.service;

import com.example.safeinventory.mapper.InventoryMapper;
import com.example.safeinventory.model.InventoryModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

public class NotSafeInventoryService {
    private static final Logger logger = LoggerFactory.getLogger(NotSafeInventoryService.class);


    @Resource
    private InventoryMapper inventoryMapper;

    // 执行不同业务场景下，具体的业务逻辑，商品售卖就是创建订单数据， 营销发券就是生成用户券数
    @Resource
    private BusinessService businessService;
    public boolean doBusiness(Integer productId, Integer quantity) {
        InventoryModel inventoryModel = inventoryMapper.selectByProductId(productId);
        if (inventoryModel.getAvailableStock() < quantity) {
            throw new RuntimeException("库存不足");
        }
        // 减库存, 并发有可能将库存扣减只小于0，  库存超卖超发
       inventoryMapper.reduceAvailableStock(productId, quantity);

        // 执行业务逻辑
        boolean result  = businessService.createBusinessDate();

        // 检查业务数据是否插入成功
        if (!result) {
            throw new RuntimeException("业务数据插入失败");
            // 抛出异常以便事务回滚
        }
        return true;

    }
}
