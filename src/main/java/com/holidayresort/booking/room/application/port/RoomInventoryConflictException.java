package com.holidayresort.booking.room.application.port;

/** A persistent room write lost a uniqueness or concurrency race. */
public final class RoomInventoryConflictException extends RuntimeException {

    private final String field;

    public RoomInventoryConflictException(String field, String message) {
        super(message);
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        this.field = field;
    }

    public String field() {
        return field;
    }
}