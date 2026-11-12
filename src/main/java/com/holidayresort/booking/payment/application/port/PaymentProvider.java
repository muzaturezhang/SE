package com.holidayresort.booking.payment.application.port;

/**
 * Port for the payment gateway. SandboxPaymentProvider is used in dev/test;
 * a real WeChat/Alipay adapter will be added in later stages.
 */
public interface PaymentProvider {

    /** Human-readable provider name, stored on the Payment record. */
    String providerName();

    /**
     * Create a payment at the provider. Returns the external payment id
     * needed to match the async callback.
     */
    String createPayment(String internalPaymentId, long amountMinor, String description);
}
