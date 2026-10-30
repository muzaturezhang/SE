package com.holidayresort.booking.room.application;

import com.holidayresort.booking.room.domain.StayPeriod;
import java.util.List;

public record RoomSearchResult(StayPeriod stay, int requestedRooms, List<RoomAvailability> options) {

    public RoomSearchResult {
        options = List.copyOf(options);
    }
}
