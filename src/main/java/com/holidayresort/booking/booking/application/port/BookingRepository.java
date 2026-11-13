package com.holidayresort.booking.booking.application.port;

import com.holidayresort.booking.booking.domain.Booking;
import java.util.List;
import java.util.Optional;

/** Persistence port for bookings; D will provide the MySQL adapter. */
public interface BookingRepository {

    Optional<Booking> findByReference(String reference);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByCustomerId(String customerId);

    List<Booking> findAll();

    Booking save(Booking booking);
}
