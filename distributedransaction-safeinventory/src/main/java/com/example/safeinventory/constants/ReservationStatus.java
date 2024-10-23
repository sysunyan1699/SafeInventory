package com.example.safeinventory.constants;

public enum ReservationStatus {
    PENDING(1, "PENDING"),
    CONFIRMED(2, "CONFIRMED"),

    ROLLBACK(3, "ROLLBACK"),

    UNKNOWN(4, "UNKNOWN");

    private int value;

    private String des;

    ReservationStatus(int value, String des) {
        this.value = value;
        this.des = des;
    }

    public int getValue() {
        return this.value;
    }

    public String getDes() {
        return des;
    }

    public static ReservationStatus valueOf(int value) {
        for (ReservationStatus m : ReservationStatus.values()) {
            if (m.getValue() == value) {
                return m;
            }
        }
        throw new IllegalArgumentException("Invalid ReservationStatus value: " + value);
    }

}
