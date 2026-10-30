package com.holidayresort.booking.room.application.port;

import com.holidayresort.booking.room.domain.RoomInventory;

/** B's read-only contract draft; D can supply a persistent adapter after review. */
public interface RoomRepository {

    RoomInventory readSnapshot();
}
