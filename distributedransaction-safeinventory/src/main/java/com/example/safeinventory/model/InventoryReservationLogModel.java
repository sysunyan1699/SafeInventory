package com.example.safeinventory.model;

import java.time.LocalDateTime;

public class InventoryReservationLogModel {
    private Long id;
    private String requestId;
    private Integer productId;
    private Integer reservationQuantity;
    private Integer status;

    private Integer verifyTryCount;

    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // Default Constructor
    public InventoryReservationLogModel() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getReservationQuantity() {
        return reservationQuantity;
    }

    public void setReservationQuantity(Integer reservationQuantity) {
        this.reservationQuantity = reservationQuantity;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getVerifyTryCount() {
        return verifyTryCount;
    }

    public void setVerifyTryCount(Integer verifyTryCount) {
        this.verifyTryCount = verifyTryCount;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "InventoryReservationLogModel{" +
                "id=" + id +
                ", requestId='" + requestId + '\'' +
                ", productId=" + productId +
                ", reservationQuantity=" + reservationQuantity +
                ", status=" + status +
                ", verifyTryCount=" + verifyTryCount +
                ", version=" + version +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
