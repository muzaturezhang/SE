package com.holidayresort.booking.persistence.booking;

import com.holidayresort.booking.booking.application.BookingException;
import com.holidayresort.booking.booking.application.port.BookingInventoryPort;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.persistence.lock.JdbcPersistenceLockService;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-owned MySQL implementation of C's booking inventory boundary.
 *
 * <p>Allocation and change use the shared ROOM_INVENTORY -> BOOKING lock order.
 * Active nights are a projection; release history remains append-only.
 */
@Repository
@Profile("mysql")
public class JdbcBookingInventoryPort implements BookingInventoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final JdbcPersistenceLockService locks;
    private final RoomQueryService roomQuery;
    private final Clock clock;

    public JdbcBookingInventoryPort(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate persistenceTransactionTemplate,
            JdbcPersistenceLockService locks,
            RoomQueryService roomQuery,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = persistenceTransactionTemplate;
        this.locks = locks;
        this.roomQuery = roomQuery;
        this.clock = clock;
    }

    @Override
    public Booking allocate(String roomTypeId, StayPeriod stay, int roomCount,
            AllocationFactory factory) {
        return transactionTemplate.execute(status -> {
            locks.lockBooking();
            RoomType type = resolveType(roomTypeId);
            List<String> roomIds = findAvailable(roomTypeId, stay, roomCount);
            Booking booking = factory.create(type, roomIds);
            reserve(booking);
            return booking;
        });
    }

    @Override
    public Booking change(Booking current, String roomTypeId, StayPeriod stay, int roomCount,
            AllocationFactory factory) {
        return transactionTemplate.execute(status -> {
            locks.lockBooking();
            releaseRows(current, "BOOKING_CHANGED", clock.instant());
            RoomType type = resolveType(roomTypeId);
            List<String> roomIds = findAvailable(roomTypeId, stay, roomCount);
            Booking changed = factory.create(type, roomIds);
            reserve(changed);
            return changed;
        });
    }

    @Override
    public void release(Booking booking, String reason) {
        transactionTemplate.executeWithoutResult(status -> {
            locks.lockBooking();
            releaseRows(booking, reason, clock.instant());
        });
    }

    private RoomType resolveType(String roomTypeId) {
        return roomQuery.listRoomTypes().stream()
                .filter(candidate -> candidate.id().equals(roomTypeId))
                .findFirst()
                .orElseThrow(() -> new BookingException(
                        BookingException.Kind.INVALID, "roomType", "Unknown room type."));
    }

    private List<String> findAvailable(String roomTypeId, StayPeriod stay, int roomCount) {
        List<String> available = jdbcTemplate.queryForList("""
                SELECT r.id
                FROM room r
                WHERE r.room_type_id = ? AND r.enabled = TRUE
                  AND NOT EXISTS (
                      SELECT 1 FROM room_block b
                      WHERE b.room_id = r.id
                        AND b.arrival_date < ? AND b.departure_date > ?)
                  AND NOT EXISTS (
                      SELECT 1 FROM booking_room_night n
                      WHERE n.room_id = r.id
                        AND n.stay_date >= ? AND n.stay_date < ?)
                ORDER BY r.id
                LIMIT ?
                """, String.class, roomTypeId, Date.valueOf(stay.departure()),
                Date.valueOf(stay.arrival()), Date.valueOf(stay.arrival()),
                Date.valueOf(stay.departure()), roomCount);
        if (available.size() < roomCount) {
            throw new BookingException(BookingException.Kind.CONFLICT, "rooms",
                    "Not enough rooms available for the selected dates. Please adjust your stay.");
        }
        return List.copyOf(available.subList(0, roomCount));
    }

    private void releaseRows(Booking booking, String reason, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO booking_room_night_history (
                    booking_reference, room_id, stay_date, released_at, release_reason)
                SELECT n.booking_reference, n.room_id, n.stay_date, ?, ?
                FROM booking_room_night n
                WHERE n.booking_reference = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM booking_room_night_history h
                      WHERE h.booking_reference = n.booking_reference
                        AND h.room_id = n.room_id
                        AND h.stay_date = n.stay_date)
                """, java.sql.Timestamp.from(now), reason, booking.reference());
        jdbcTemplate.update("DELETE FROM booking_room_night WHERE booking_reference = ?",
                booking.reference());
        jdbcTemplate.update("""
                UPDATE booking_room_allocation
                SET released_at = ?, release_reason = ?
                WHERE booking_reference = ? AND released_at IS NULL
                """, java.sql.Timestamp.from(now), reason, booking.reference());
    }

    private void reserve(Booking booking) {
        Instant now = clock.instant();
        List<Object[]> nights = new ArrayList<>();
        for (String roomId : booking.allocatedRoomIds()) {
            int updated = jdbcTemplate.update("""
                    UPDATE booking_room_allocation
                    SET allocated_at = ?, released_at = NULL, release_reason = NULL
                    WHERE booking_reference = ? AND room_id = ?
                    """, java.sql.Timestamp.from(now), booking.reference(), roomId);
            if (updated == 0) {
                jdbcTemplate.update("""
                        INSERT INTO booking_room_allocation (
                            booking_reference, room_id, allocated_at)
                        VALUES (?, ?, ?)
                        """, booking.reference(), roomId, java.sql.Timestamp.from(now));
            }
            LocalDate day = booking.stay().arrival();
            while (day.isBefore(booking.stay().departure())) {
                nights.add(new Object[]{roomId, Date.valueOf(day), booking.reference()});
                day = day.plusDays(1);
            }
        }
        for (Object[] night : nights) {
            jdbcTemplate.update("""
                    INSERT INTO booking_room_night (room_id, stay_date, booking_reference)
                    VALUES (?, ?, ?)
                    """, night[0], night[1], night[2]);
        }
    }
}
