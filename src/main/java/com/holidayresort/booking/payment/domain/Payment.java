package com.holidayresort.booking.payment.domain;

import java.time.LocalDateTime;

/**
 * One payment attempt for a booking. Amount is CNY integer minor units.
 *
 * <p>idempotencyKey ensures that retried initiation does not create two
 * payments for the same booking. providerReference is the external transaction id.
 */
public record Payment(
        String id,
        String bookingReference,
        String idempotencyKey,
        long amountMinor,
        PaymentStatus status,
        String providerReference,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {

    public Payment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Payment id is required");
        }
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("Booking reference is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
    }

    public Payment withStatus(PaymentStatus newStatus, String providerReference, String failureReason) {
        return new Payment(id, bookingReference, idempotencyKey, amountMinor,
                newStatus, providerReference != null ? providerReference : this.providerReference,
                failureReason, createdAt,
                newStatus == PaymentStatus.PAID ? LocalDateTime.now() : this.paidAt);
    }
}
