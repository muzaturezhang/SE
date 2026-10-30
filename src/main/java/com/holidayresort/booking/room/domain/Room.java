package com.holidayresort.booking.room.domain;

/** One physical room; enabled is an administrative flag, not date availability. */
public record Room(String id, String roomTypeId, boolean enabled) {

    public Room {
        if (id == null || id.isBlank() || roomTypeId == null || roomTypeId.isBlank()) {
            throw new IllegalArgumentException("Room and room type identifiers are required");
        }
    }
}
