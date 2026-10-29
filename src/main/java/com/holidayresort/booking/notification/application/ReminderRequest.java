package com.holidayresort.booking.notification.application;

import java.time.LocalDate;

/** Booking-facing request for the currently scoped one-day reminder. */
public record ReminderRequest(
        String bookingReference,
        String recipientEmail,
        LocalDate checkInDate) {
}
