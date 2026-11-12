package com.holidayresort.booking.payment.infrastructure;

import com.holidayresort.booking.payment.application.port.PaymentProvider;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sandbox payment provider. No real network calls; simulates Alipay sandbox.
 */
@Component
@Profile("!mysql & (dev | test)")
public class SandboxPaymentProvider implements PaymentProvider {

    private final AtomicLong counter = new AtomicLong(1000);
    private final Map<String, Result> transactions = new ConcurrentHashMap<>();

    @Override
    public Initiation initiate(String bookingReference, long amountMinor, String customerId) {
        String ref = "SBX-" + counter.getAndIncrement();
        transactions.put(ref, new Result(Result.Outcome.PENDING, ref, "waiting for payment"));
        return new Initiation(ref, "/sandbox/checkout?ref=" + ref);
    }

    @Override
    public Result query(String providerReference) {
        return transactions.getOrDefault(providerReference,
                new Result(Result.Outcome.UNKNOWN, providerReference, "transaction not found"));
    }

    /** Sandbox control: simulate a successful callback. */
    public void simulateSuccess(String providerReference) {
        transactions.put(providerReference,
                new Result(Result.Outcome.SUCCESS, providerReference, "payment success"));
    }

    /** Sandbox control: simulate a definitive rejection. */
    public void simulateFailure(String providerReference, String reason) {
        transactions.put(providerReference,
                new Result(Result.Outcome.FAILED, providerReference, reason));
    }

    /** Sandbox control: simulate an unknown / timeout result. */
    public void simulateUnknown(String providerReference) {
        transactions.put(providerReference,
                new Result(Result.Outcome.UNKNOWN, providerReference, "result unknown"));
    }
}
