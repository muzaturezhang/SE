package com.holidayresort.booking.notification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable values required to create or replace one idempotent reminder schedule. */
public record ReminderDraft(
        String bookingReference,
        String recipientEmail,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String roomTypeName,
        LocalDate remindOnDate,
        ReminderKind kind,
        Instant scheduledAt,
        Instant createdAt) {

    public ReminderDraft {
        bookingReference = requireText(bookingReference, "bookingReference");
        recipientEmail = requireText(recipientEmail, "recipientEmail");
        checkInDate = Objects.requireNonNull(checkInDate, "checkInDate must not be null");
        checkOutDate = Objects.requireNonNull(checkOutDate, "checkOutDate must not be null");
        roomTypeName = requireText(roomTypeName, "roomTypeName");
        remindOnDate = Objects.requireNonNull(remindOnDate, "remindOnDate must not be null");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (!checkOutDate.isAfter(checkInDate)) {
            throw new IllegalArgumentException("checkOutDate must be after checkInDate");
        }
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