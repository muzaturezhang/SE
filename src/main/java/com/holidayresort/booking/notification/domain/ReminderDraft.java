package com.holidayresort.booking.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable values required to create one idempotent reminder schedule. */
public record ReminderDraft(
        String bookingReference,
        String recipientEmail,
        LocalDate checkInDate,
        LocalDate remindOnDate,
        ReminderKind kind,
        Instant createdAt) {

    public ReminderDraft {
        bookingReference = requireText(bookingReference, "bookingReference");
        recipientEmail = requireText(recipientEmail, "recipientEmail");
        checkInDate = Objects.requireNonNull(checkInDate, "checkInDate must not be null");
        remindOnDate = Objects.requireNonNull(remindOnDate, "remindOnDate must not be null");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (!checkInDate.minusDays(kind.daysBefore()).equals(remindOnDate)) {
            throw new IllegalArgumentException("remindOnDate does not match reminder kind");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
