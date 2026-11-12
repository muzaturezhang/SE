package com.holidayresort.booking.payment.domain;

/**
 * Payment lifecycle state machine (DS 5.3).
 *
 * NEW -> PENDING -> PAID (terminal, exactly once)
 *                 -> FAILED (terminal, definitive rejection)
 *                 -> UNDER_REVIEW (result unknown, staff reconciles)
 *                 -> EXPIRED (hold timeout)
 */
public enum PaymentStatus {
    /** Just created, not yet sent to the provider. */
    NEW,
    /** Initiated at the provider, waiting for result. */
    PENDING,
    /** Confirmed success. Terminal. */
    PAID,
    /** Definitive rejection. Terminal. */
    FAILED,
    /** Result cannot be determined; staff must reconcile. */
    UNDER_REVIEW,
    /** Hold expired before payment completed. */
    EXPIRED;

    public boolean isTerminal() {
        return this == PAID || this == FAILED;
    }
}
