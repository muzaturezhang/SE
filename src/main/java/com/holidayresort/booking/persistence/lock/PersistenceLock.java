package com.holidayresort.booking.persistence.lock;

/** Canonical lock order shared by room, booking and payment workflows. */
public enum PersistenceLock {
    ROOM_INVENTORY("ROOM_INVENTORY"),
    BOOKING("BOOKING"),
    PAYMENT("PAYMENT");

    private final String databaseId;

    PersistenceLock(String databaseId) {
        this.databaseId = databaseId;
    }

    public String databaseId() {
        return databaseId;
    }
}