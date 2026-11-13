package com.holidayresort.booking.booking.application;

public class BookingException extends RuntimeException {

    public enum Kind { INVALID, CONFLICT, NOT_FOUND }

    private final Kind kind;
    private final String field;

    public BookingException(Kind kind, String field, String message) {
        super(message);
        this.kind = kind;
        this.field = field;
    }

    public Kind kind() {
        return kind;
    }

    public String field() {
        return field;
    }
}
