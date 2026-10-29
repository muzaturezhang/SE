package com.holidayresort.booking.notification.application;

import java.util.Objects;

/** Caller-actionable notification errors without exposing provider details. */
public final class NotificationException extends RuntimeException {

    public enum Kind {
        INVALID_REQUEST,
        CONFLICT
    }

    private final Kind kind;
    private final String field;

    public NotificationException(Kind kind, String field, String message) {
        super(message);
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.field = field;
    }

    public Kind kind() {
        return kind;
    }

    public String field() {
        return field;
    }
}
