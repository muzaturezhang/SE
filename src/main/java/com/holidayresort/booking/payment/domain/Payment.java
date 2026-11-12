package com.holidayresort.booking.payment.domain;

import java.time.LocalDateTime;

/**
 * A payment record linked to a booking. Amount is in CNY minor units (fen).
 * providerPaymentId is returned by the payment gateway after successful init.
 */
public record Payment(
        String paymentId,
        String bookingReference,
        long amountMinor,
        PaymentStatus status,
        String provider,
        String providerPaymentId,
        LocalDateTime createdAt,
        LocalDateTime paidAt) {

    public Payment {
        if (paymentId == null || paymentId.isBlank()) {
            throw new IllegalArgumentException("Payment id is required");
        }
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("Booking reference is required");
        }
        if (amountMinor <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
    }

    public Payment withStatus(PaymentStatus newStatus) {
        return new Payment(paymentId, bookingReference, amountMinor, newStatus,
                provider, providerPaymentId, createdAt,
                newStatus == PaymentStatus.SUCCEEDED ? LocalDateTime.now() : paidAt);
    }

    public Payment withProviderPaymentId(String externalId) {
        return new Payment(paymentId, bookingReference, amountMinor, status,
                provider, externalId, createdAt, paidAt);
    }
}
