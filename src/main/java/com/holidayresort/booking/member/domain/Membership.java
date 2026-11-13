package com.holidayresort.booking.member.domain;

import java.time.LocalDate;

/**
 * Membership application or entitlement for one customer. An unconfirmed
 * application carries no effective dates; dates exist only after the payment
 * service confirms it.
 */
public record Membership(String customerId, boolean paymentConfirmed, long feeMinor,
                         int discountPercent, LocalDate startDate, LocalDate endDate) {

    public enum State { PENDING, ACTIVE, EXPIRED }

    public Membership {
        if (feeMinor < 1) {
            throw new IllegalArgumentException("Membership fee must be positive integer minor units");
        }
        if (discountPercent < 0 || discountPercent > 99) {
            throw new IllegalArgumentException("Membership discount must be between 0 and 99 percent");
        }
        if (paymentConfirmed && (startDate == null || endDate == null || endDate.isBefore(startDate))) {
            throw new IllegalArgumentException("Confirmed membership requires valid effective dates");
        }
        if (!paymentConfirmed && (startDate != null || endDate != null)) {
            throw new IllegalArgumentException("Unconfirmed membership cannot carry effective dates");
        }
    }

    public State stateOn(LocalDate today) {
        if (!paymentConfirmed || startDate == null || endDate == null) {
            return State.PENDING;
        }
        return today.isAfter(endDate) ? State.EXPIRED : State.ACTIVE;
    }
}
