package com.holidayresort.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the booking module.
 * Bound to app.booking-hold-minutes in application.properties.
 */
@ConfigurationProperties(prefix = "app")
public record BookingProperties(int bookingHoldMinutes) {

    public BookingProperties {
        if (bookingHoldMinutes <= 0) {
            bookingHoldMinutes = 15;
        }
    }
}
