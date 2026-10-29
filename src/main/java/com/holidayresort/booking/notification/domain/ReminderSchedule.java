package com.holidayresort.booking.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Persisted reminder state. All timestamps are UTC instants. */
public record ReminderSchedule(
        long id,
        String bookingReference,
        String recipientEmail,
        LocalDate checkInDate,
        LocalDate remindOnDate,
        ReminderKind kind,
        ReminderStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant lastAttemptAt,
        Instant sentAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public ReminderSchedule {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive");
        }
        bookingReference = Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        recipientEmail = Objects.requireNonNull(recipientEmail, "recipientEmail must not be null");
        checkInDate = Objects.requireNonNull(checkInDate, "checkInDate must not be null");
        remindOnDate = Objects.requireNonNull(remindOnDate, "remindOnDate must not be null");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (attemptCount < 0 || version < 0) {
            throw new IllegalArgumentException("attemptCount and version must not be negative");
        }
    }
}
