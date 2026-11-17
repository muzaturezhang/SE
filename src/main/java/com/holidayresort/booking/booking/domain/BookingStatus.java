package com.holidayresort.booking.booking.domain;

/**
 * Lifecycle states of a booking.
 *
 * PENDING  — created, room held, waiting for payment within the deadline
 * PAID     — payment confirmed, room is committed to the guest
 * FAILED   — payment rejected, room released
 * UNDER_REVIEW — payment callback result unknown, staff must reconcile
 * EXPIRED  — hold timed out without payment, room released
 * CANCELLED — customer cancelled before stay, room released
 */
public enum BookingStatus {
    PENDING,
    PAID,
    FAILED,
    UNDER_REVIEW,
    EXPIRED,
    CANCELLED;

    public boolean isTerminal() {
        return this == PAID || this == FAILED || this == EXPIRED || this == CANCELLED;
    }
}
