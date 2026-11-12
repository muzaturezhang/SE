package com.holidayresort.booking.payment.application.port;

/**
 * C-owned port for the external payment provider. The sandbox adapter lives in
 * infrastructure; the domain never depends on a vendor SDK or JSON types.
 */
public interface PaymentProvider {

    /** Initiate a payment; returns the provider-side transaction reference. */
    Initiation initiate(String bookingReference, long amountMinor, String customerId);

    /** Query the current status of a previously initiated payment. */
    Result query(String providerReference);

    record Initiation(String providerReference, String checkoutUrl) {
    }

    /**
     * Outcome of a provider query. Classification:
     * SUCCESS = terminal success,
     * FAILED = definitive rejection,
     * UNKNOWN = result cannot be determined (staff must reconcile),
     * PENDING = still processing.
     */
    record Result(Outcome outcome, String providerReference, String message) {
        public enum Outcome { SUCCESS, FAILED, PENDING, UNKNOWN }
    }
}
