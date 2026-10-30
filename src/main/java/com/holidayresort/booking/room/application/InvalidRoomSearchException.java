package com.holidayresort.booking.room.application;

/** Field names identify the rejected part of the query, not shared business states. */
public class InvalidRoomSearchException extends IllegalArgumentException {

    private final String field;

    public InvalidRoomSearchException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
