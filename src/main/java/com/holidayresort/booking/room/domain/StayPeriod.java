package com.holidayresort.booking.room.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** A stay includes arrival and excludes departure. */
public record StayPeriod(LocalDate arrival, LocalDate departure) {

    public StayPeriod {
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(departure, "departure");
        if (!departure.isAfter(arrival)) {
            throw new IllegalArgumentException("Departure must be after arrival");
        }
    }

    public long nights() {
        return ChronoUnit.DAYS.between(arrival, departure);
    }

    public boolean overlaps(StayPeriod other) {
        return arrival.isBefore(other.departure) && other.arrival.isBefore(departure);
    }
}
