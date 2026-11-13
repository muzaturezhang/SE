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

/** Development adapter; D provides the MySQL adapter. */
@Repository
@Profile("!mysql & (dev | test)")
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<String, Payment> byId = new ConcurrentHashMap<>();
    private final Map<String, Payment> byBooking = new ConcurrentHashMap<>();
    private final Map<String, Payment> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public Optional<Payment> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Payment> findByBookingReference(String bookingReference) {
        return Optional.ofNullable(byBooking.get(bookingReference));
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public List<Payment> findAll() {
        return List.copyOf(byId.values());
    }

    @Override
    public synchronized Payment save(Payment payment) {
        byId.put(payment.id(), payment);
        byBooking.put(payment.bookingReference(), payment);
        if (payment.idempotencyKey() != null && !payment.idempotencyKey().isBlank()) {
            byIdempotencyKey.put(payment.idempotencyKey(), payment);
        }
        return payment;
    }
}
