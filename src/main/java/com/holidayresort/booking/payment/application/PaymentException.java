package com.holidayresort.booking.payment.application;

public class PaymentException extends RuntimeException {

    public enum Kind { INVALID, CONFLICT, NOT_FOUND, PROVIDER_ERROR }

    private final Kind kind;

    public PaymentException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public PaymentException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
