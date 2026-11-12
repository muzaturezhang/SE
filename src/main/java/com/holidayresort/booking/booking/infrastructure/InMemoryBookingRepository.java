package com.holidayresort.booking.booking.infrastructure;

import com.holidayresort.booking.booking.application.port.BookingRepository;
import com.holidayresort.booking.booking.domain.Booking;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** In-memory booking store for dev/test; D will replace with MySQL. */
@Repository
@Profile("!mysql")
public class InMemoryBookingRepository implements BookingRepository {

    private final Map<String, Booking> store = new ConcurrentHashMap<>();

    @Override
    public Optional<Booking> findByReference(String reference) {
        return Optional.ofNullable(store.get(reference));
    }

    @Override
    public Optional<Booking> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        return store.values().stream()
                .filter(b -> idempotencyKey.equals(b.idempotencyKey()))
                .findFirst();
    }

    @Override
    public List<Booking> findByCustomerId(String customerId) {
        return store.values().stream()
                .filter(b -> customerId.equals(b.customerId()))
                .sorted((a, c) -> c.createdAt().compareTo(a.createdAt()))
                .toList();
    }

    @Override
    public List<Booking> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Booking save(Booking booking) {
        store.put(booking.reference(), booking);
        return booking;
    }
}
