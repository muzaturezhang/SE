package com.holidayresort.booking.member.domain;

import java.time.LocalDate;
import java.util.Locale;

/**
 * A registered CUSTOMER identity. Membership is a business status of this
 * customer and never an administrative role.
 */
public record Customer(String id, String displayName, String email, String passwordHash, LocalDate registeredOn) {

    public Customer {
        id = id.strip();
        displayName = displayName.strip();
        email = email.strip().toLowerCase(Locale.ROOT);
    }
}
