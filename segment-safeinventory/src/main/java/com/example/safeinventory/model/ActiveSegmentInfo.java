package com.example.safeinventory.model;


/**
 * 分段信息数据类，增加版本号字段
 */
public class ActiveSegmentInfo {
    private int currentPointer;  // 当前正在使用的分段ID
    private int totalSegments;   // 该商品的总分段数
    private long version;         // 版本号

    public ActiveSegmentInfo(int currentPointer, int totalSegments, long version) {
        this.currentPointer = currentPointer;
        this.totalSegments = totalSegments;
        this.version = version;
    }

    public void setCurrentPointer(int currentPointer) {
        this.currentPointer = currentPointer;
    }

    public void setTotalSegments(int totalSegments) {
        this.totalSegments = totalSegments;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public int getCurrentPointer() {
        return currentPointer;
    }

    public int getTotalSegments() {
        return totalSegments;
    }

    public long getVersion() {
        return version;
    }


    @Override
    public String toString() {
        return "ActiveSegmentInfo{" +
                "currentPointer=" + currentPointer +
                ", totalSegments=" + totalSegments +
                ", version=" + version +
                '}';
    }
}
