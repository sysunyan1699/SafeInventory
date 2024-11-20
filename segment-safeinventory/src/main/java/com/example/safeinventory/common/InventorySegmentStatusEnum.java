package com.example.safeinventory.common;

/**
 * 库存分段状态枚举
 */
public enum InventorySegmentStatusEnum {
    
    VALID(1, "库存分段生效中"),
    INVALID(-1, "库存分段无效");

    private final int code;
    private final String description;

    InventorySegmentStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static InventorySegmentStatusEnum getByCode(int code) {
        for (InventorySegmentStatusEnum status : values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
} 