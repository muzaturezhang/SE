package com.holidayresort.booking.notification.application;

import java.time.LocalDate;

/**
 * Booking-facing command for one reminder revision.
 *
 * <p>The fields are snapshots supplied by C. Notification does not query booking or member
 * tables, and the room type is stored as display text for the email.
 */
public record ReminderRequest(
        String bookingReference,
        String recipientEmail,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String roomTypeName) {
}