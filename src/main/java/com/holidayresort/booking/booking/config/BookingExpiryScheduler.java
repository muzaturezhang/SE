package com.holidayresort.booking.booking.config;

import com.holidayresort.booking.booking.application.BookingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** D-owned trigger for C's hold-expiry operation; opt-in to avoid test side effects. */
@Component
@ConditionalOnProperty(prefix = "booking", name = "expiry-enabled", havingValue = "true")
public class BookingExpiryScheduler {

    private final BookingService bookings;

    public BookingExpiryScheduler(BookingService bookings) {
        this.bookings = bookings;
    }

    @Scheduled(fixedDelayString = "${booking.expiry-interval-ms:60000}")
    public void releaseExpired() {
        bookings.releaseExpiredBookings();
    }
}
