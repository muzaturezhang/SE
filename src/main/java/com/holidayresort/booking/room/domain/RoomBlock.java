package com.holidayresort.booking.room.domain;

import java.util.Objects;

/** Read-only unavailability input. It does not define a Booking or Payment state. */
public record RoomBlock(String roomId, StayPeriod period) {

    public RoomBlock {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room identifier is required");
        }
        Objects.requireNonNull(period, "period");
    }
}
