package com.holidayresort.booking.payment.application;

public class PaymentException extends RuntimeException {

    public enum Kind { INVALID, CONFLICT, NOT_FOUND, PROVIDER_ERROR }

    private final Kind kind;
    private final String field;

    public PaymentException(Kind kind, String field, String message) {
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
