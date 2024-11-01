package com.example.safeinventory.common;

public enum RedisReduceStockEnum {
    STOCK_IS_NULL(-2, "库存不存在"),
    STOCK_IS_NOT_ENOUGH(-1, "库存不足"),
    STOCK_EXHAUSTED(0, "库存已完全使用"),
    REDUCE_SUCCESS(1, "库存扣减成功");


    private int value;

    private String des;


    RedisReduceStockEnum(int value, String des) {
        this.value = value;
        this.des = des;
    }

    public int getValue() {
        return this.value;
    }

    public String getDes() {
        return des;
    }

    public static RedisReduceStockEnum valueOf(int value) {
        for (RedisReduceStockEnum m : RedisReduceStockEnum.values()) {
            if (m.getValue() == value) {
                return m;
            }
        }
        throw new IllegalArgumentException("Invalid RedisReduceStockEnum value: " + value);
    }
}
