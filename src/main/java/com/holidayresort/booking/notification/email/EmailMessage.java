package com.holidayresort.booking.notification.email;

import java.util.Objects;

/** Provider-neutral plain-text email. */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        to = requireHeader(to, "to");
        subject = requireHeader(subject, "subject");
        body = Objects.requireNonNull(body, "body must not be null");
    }

    private static String requireHeader(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(field + " must not contain line breaks");
        }
        return value;
    }
}
