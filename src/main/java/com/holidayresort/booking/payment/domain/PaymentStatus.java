package com.holidayresort.booking.payment.domain;

/**
 * Lifecycle states of a payment.
 *
 * INITIATED  — payment request sent to provider, waiting for callback
 * SUCCEEDED  — provider confirmed payment, booking becomes PAID
 * FAILED     — provider rejected or user cancelled
 * UNDER_REVIEW — callback result unknown, staff must reconcile
 */
public enum PaymentStatus {
    INITIATED,
    SUCCEEDED,
    FAILED,
    UNDER_REVIEW
}
