package com.example.safeinventory.model;

import java.time.LocalDateTime;

public class InventoryModel {

    private Long id;
    private Integer productId;
    private Integer totalStock;
    private Integer availableStock;

    private Integer version;

    private Integer currentSegmentPointer;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;


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

    public Integer getCurrentSegmentPointer() {
        return currentSegmentPointer;
    }

    public void setCurrentSegmentPointer(Integer currentSegmentPointer) {
        this.currentSegmentPointer = currentSegmentPointer;
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
        return "InventoryModel{" +
                "id=" + id +
                ", productId=" + productId +
                ", totalStock=" + totalStock +
                ", availableStock=" + availableStock +
                ", version=" + version +
                ", currentSegmentPointer=" + currentSegmentPointer +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
