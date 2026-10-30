package com.holidayresort.booking.room.domain;

import java.util.List;

/** Base rate is CNY minor units per physical room per night. */
public record RoomType(
        String id,
        String name,
        String englishName,
        String description,
        String imagePath,
        String imageAlt,
        List<String> features,
        long nightlyRateMinor) {

    public RoomType {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Room type identifier and name are required");
        }
        if (nightlyRateMinor <= 0) {
            throw new IllegalArgumentException("Nightly rate must be positive CNY minor units");
        }
        features = List.copyOf(features);
    }
}
