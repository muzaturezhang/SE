package com.holidayresort.booking.room.application.port;

import com.holidayresort.booking.room.domain.RoomInventory;
import java.util.function.UnaryOperator;

/** Draft B/D port: validation and replacement must execute within one atomic write boundary. */
public interface RoomInventoryEditor extends RoomRepository {
    void updateSnapshot(UnaryOperator<RoomInventory> change);
}
