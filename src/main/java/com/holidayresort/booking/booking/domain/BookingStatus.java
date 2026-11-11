package com.holidayresort.booking.booking.domain;

/**
 * Lifecycle states of a booking.
 *
 * PENDING  — created, room held, waiting for payment within the deadline
 * PAID     — payment confirmed, room is committed to the guest
 * FAILED   — payment rejected or cancelled, room released
 * UNDER_REVIEW — payment callback result unknown, staff must reconcile
 * EXPIRED  — hold timed out without payment, room released
 */
public enum BookingStatus {
    PENDING,
    PAID,
    FAILED,
    UNDER_REVIEW,
    EXPIRED
}
