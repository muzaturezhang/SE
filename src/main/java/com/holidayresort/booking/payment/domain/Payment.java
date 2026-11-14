package com.holidayresort.booking.payment.domain;

import java.time.LocalDateTime;

/**
 * One payment attempt. Amount is CNY integer minor units.
 *
 * <p>BOOKING payments have a booking reference and a customer. MEMBERSHIP
 * payments have a customer and deliberately have no booking reference.
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
        LocalDateTime paidAt,
        PaymentPurpose purpose,
        String customerId) {

    /** Compatibility constructor for existing booking-only callers/tests. */
    public Payment(String id, String bookingReference, String idempotencyKey, long amountMinor,
            PaymentStatus status, String providerReference, String failureReason,
            LocalDateTime createdAt, LocalDateTime paidAt) {
        this(id, bookingReference, idempotencyKey, amountMinor, status, providerReference,
                failureReason, createdAt, paidAt, PaymentPurpose.BOOKING, null);
    }

    public Payment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Payment id is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("Payment purpose is required");
        }
        if (purpose == PaymentPurpose.BOOKING
                && (bookingReference == null || bookingReference.isBlank())) {
            throw new IllegalArgumentException("A booking payment requires a booking reference");
        }
        if (purpose == PaymentPurpose.MEMBERSHIP && customerId == null) {
            throw new IllegalArgumentException("A membership payment requires a customer id");
        }
    }

    public Payment withStatus(PaymentStatus newStatus, String providerReference, String failureReason) {
        return withStatus(newStatus, providerReference, failureReason,
                newStatus == PaymentStatus.PAID ? LocalDateTime.now() : paidAt);
    }

    public Payment withStatus(PaymentStatus newStatus, String providerReference,
            String failureReason, LocalDateTime paidAt) {
        return new Payment(id, bookingReference, idempotencyKey, amountMinor,
                newStatus, providerReference != null ? providerReference : this.providerReference,
                failureReason, createdAt,
                newStatus == PaymentStatus.PAID ? paidAt : this.paidAt, purpose, customerId);
    }
}
