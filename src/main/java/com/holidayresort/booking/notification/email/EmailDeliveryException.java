package com.holidayresort.booking.notification.email;

/** Raised when an email adapter cannot hand a message to the provider. */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
