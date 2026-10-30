package com.holidayresort.booking.room.application;

import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomType;

public record ManagedRoom(Room room, RoomType type, boolean activeOrFutureBlock) {
}
