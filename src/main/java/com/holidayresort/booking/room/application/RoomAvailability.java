package com.holidayresort.booking.room.application;

import com.holidayresort.booking.room.domain.RoomType;

public record RoomAvailability(RoomType roomType, int availableRooms) {
}
