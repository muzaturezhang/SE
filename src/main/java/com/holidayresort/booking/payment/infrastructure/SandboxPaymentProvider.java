package com.holidayresort.booking.payment.infrastructure;

import com.holidayresort.booking.payment.application.port.PaymentProvider;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sandbox payment provider for dev/test. Does not call any real gateway;
 * returns a fake external id. The callback is triggered manually in tests.
 */
@Component
@Profile("!mysql")
public class SandboxPaymentProvider implements PaymentProvider {

    @Override
    public String providerName() {
        return "sandbox";
    }

    @Override
    public String createPayment(String internalPaymentId, long amountMinor, String description) {
        return "SBX-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
