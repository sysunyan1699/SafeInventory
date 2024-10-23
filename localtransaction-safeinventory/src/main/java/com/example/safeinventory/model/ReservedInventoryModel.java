package com.example.safeinventory.model;

import java.time.LocalDateTime;

public class ReservedInventoryModel {
    private Long id;
    private Integer productId;
    private Integer totalStock;
    private Integer availableStock;
    private Integer reservedStock;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // Default Constructor
    public ReservedInventoryModel() {}

    // Parameterized Constructor
    public ReservedInventoryModel(Long id, Integer productId, Integer totalStock, Integer availableStock, Integer reservedStock, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.productId = productId;
        this.totalStock = totalStock;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(Integer availableStock) {
        this.availableStock = availableStock;
    }

    public Integer getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(Integer reservedStock) {
        this.reservedStock = reservedStock;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    // toString method for easier printing
    @Override
    public String toString() {
        return "ReservedInventoryModel{" +
                "id=" + id +
                ", productId=" + productId +
                ", totalStock=" + totalStock +
                ", availableStock=" + availableStock +
                ", reservedStock=" + reservedStock +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
