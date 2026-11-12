package com.holidayresort.booking.payment.application.port;

import com.holidayresort.booking.payment.domain.Payment;
import java.util.List;
import java.util.Optional;

/** Persistence port for payments; D will provide the MySQL adapter. */
public interface PaymentRepository {

    Optional<Payment> findById(String id);

    Optional<Payment> findByBookingReference(String bookingReference);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findAll();

    Payment save(Payment payment);
}
