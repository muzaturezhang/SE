package com.holidayresort.booking.booking.application.port;

import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.util.List;

/**
 * C/D boundary for atomic booking inventory allocation and release.
 *
 * <p>The implementation obtains the shared persistence locks, re-reads the
 * current availability, invokes the factory to persist the booking, and then
 * writes the physical allocation in the same transaction.
 */
public interface BookingInventoryPort {

    Booking allocate(String roomTypeId, StayPeriod stay, int roomCount, AllocationFactory factory);

    void release(Booking booking, String reason);

    @FunctionalInterface
    interface AllocationFactory {
        Booking create(RoomType roomType, List<String> roomIds);
    }
}
