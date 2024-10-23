package com.example.safeinventory.model;

import java.time.LocalDateTime;

public class InventoryModel {

    private Long id;
    private Integer productId;
    private Integer totalStock;
    private Integer availableStock;

    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // Default Constructor

    // Parameterized Constructor


    public InventoryModel(Long id, Integer productId, Integer totalStock, Integer availableStock, Integer version, LocalDateTime createTime, LocalDateTime updateTime) {
        this.id = id;
        this.productId = productId;
        this.totalStock = totalStock;
        this.availableStock = availableStock;
        this.version = version;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    @Override
    public String toString() {
        return "InventoryWithVersionModel{" +
                "id=" + id +
                ", productId=" + productId +
                ", totalStock=" + totalStock +
                ", availableStock=" + availableStock +
                ", version=" + version +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
