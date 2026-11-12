package com.holidayresort.booking.payment.infrastructure;

import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.Payment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** In-memory payment store for dev/test; D will replace with MySQL. */
@Repository
@Profile("!mysql")
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<String, Payment> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Payment> findByPaymentId(String paymentId) {
        return Optional.ofNullable(store.get(paymentId));
    }

    @Override
    public Optional<Payment> findByProviderPaymentId(String providerPaymentId) {
        if (providerPaymentId == null) return Optional.empty();
        return store.values().stream()
                .filter(p -> providerPaymentId.equals(p.providerPaymentId()))
                .findFirst();
    }

    @Override
    public List<Payment> findByBookingReference(String bookingReference) {
        return store.values().stream()
                .filter(p -> bookingReference.equals(p.bookingReference()))
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    @Override
    public List<Payment> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Payment save(Payment payment) {
        store.put(payment.paymentId(), payment);
        return payment;
    }
}
