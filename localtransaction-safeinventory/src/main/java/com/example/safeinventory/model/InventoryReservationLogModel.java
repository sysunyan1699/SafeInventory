package com.example.safeinventory.model;

import java.time.LocalDateTime;

public class InventoryReservationLogModel {
    private Long id;
    private String requestId;
    private Integer productId;
    private Integer reservationQuantity;
    private Integer reservationStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // Default Constructor
    public InventoryReservationLogModel() {}


    // Getters and Setters
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

    public Integer getReservationStatus() {
        return reservationStatus;
    }

    public void setReservationStatus(Integer reservationStatus) {
        this.reservationStatus = reservationStatus;
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
        return "InventoryReservationLogModel{" +
                "id=" + id +
                ", requestId='" + requestId + '\'' +
                ", productId=" + productId +
                ", reservationQuantity=" + reservationQuantity +
                ", reservationStatus=" + reservationStatus +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
