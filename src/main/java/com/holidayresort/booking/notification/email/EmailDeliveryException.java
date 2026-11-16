package com.holidayresort.booking.notification.email;

/** Raised when an email adapter cannot hand a message to the provider. */
public class EmailDeliveryException extends RuntimeException {

    private final String deliveryCode;

    public EmailDeliveryException(String message) {
        this("EMAIL_DELIVERY_FAILED", message, null);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        this("EMAIL_DELIVERY_FAILED", message, cause);
    }

    public EmailDeliveryException(String deliveryCode, String message) {
        this(deliveryCode, message, null);
    }

    public EmailDeliveryException(String deliveryCode, String message, Throwable cause) {
        super(message, cause);
        if (deliveryCode == null || deliveryCode.isBlank()) {
            throw new IllegalArgumentException("deliveryCode must not be blank");
        }
        this.deliveryCode = deliveryCode;
    }

    public String deliveryCode() {
        return deliveryCode;
    }
}