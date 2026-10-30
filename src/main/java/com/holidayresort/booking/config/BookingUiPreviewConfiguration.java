package com.holidayresort.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Enables only B's local, explicitly labelled UI fixture pages. */
@Configuration
@Profile("booking-ui")
public class BookingUiPreviewConfiguration {
    public BookingUiPreviewConfiguration(Environment environment, @Value("${server.address}") String address) {
        if (!environment.matchesProfiles("dev") || environment.matchesProfiles("mysql", "test")
                || !(address.equals("127.0.0.1") || address.equals("::1"))) {
            throw new IllegalStateException("booking-ui requires dev, loopback binding, and no mysql/test profile");
        }
    }
}
