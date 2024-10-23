package com.example.safeinventory.common;

public class BusinessException extends RuntimeException{
    private int errorCode; // 可以根据需要加入错误码，便于区分不同异常
    private String errorMessage;

    // 构造方法，传入错误信息
    public BusinessException(String errorMessage) {
        super(errorMessage);
        this.errorMessage = errorMessage;
    }

    // 构造方法，传入错误码和错误信息
    public BusinessException(int errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    // 获取错误码
    public int getErrorCode() {
        return errorCode;
    }

    // 获取错误信息
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "BusinessException{" +
                "errorCode=" + errorCode +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
