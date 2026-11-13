package com.holidayresort.booking.booking.domain;

import com.holidayresort.booking.room.domain.StayPeriod;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One customer's booking. Created in PENDING with a 15-minute payment deadline;
 * moves to PAID on successful payment, FAILED on terminal rejection,
 * UNDER_REVIEW when the provider result is unknown, or EXPIRED on hold timeout.
 *
 * <p>The idempotencyKey is supplied by the client so a retried submit does not
 * create two bookings for the same intent.
 *
 * <p>allocatedRoomIds records the physical rooms locked for this booking; it is
 * used to release the inventory when the hold expires or payment fails.
 */
public record Booking(
        String reference,
        String idempotencyKey,
        String customerId,
        String guestName,
        String guestEmail,
        String roomTypeId,
        StayPeriod stay,
        int roomCount,
        List<String> allocatedRoomIds,
        PriceSnapshot price,
        BookingStatus status,
        LocalDateTime createdAt,
        LocalDateTime paymentDeadline) {

    public Booking {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Booking reference is required");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer id is required");
        }
        if (guestName == null || guestName.isBlank()) {
            throw new IllegalArgumentException("Guest name is required");
        }
        if (guestEmail == null || guestEmail.isBlank()) {
            throw new IllegalArgumentException("Guest email is required");
        }
        if (roomTypeId == null || roomTypeId.isBlank()) {
            throw new IllegalArgumentException("Room type id is required");
        }
        if (roomCount <= 0) {
            throw new IllegalArgumentException("Room count must be positive");
        }
        allocatedRoomIds = List.copyOf(allocatedRoomIds == null ? List.of() : allocatedRoomIds);
        if (status == BookingStatus.PENDING && paymentDeadline == null) {
            throw new IllegalArgumentException("PENDING booking requires a payment deadline");
        }
    }

    public Booking withStatus(BookingStatus newStatus) {
        return new Booking(reference, idempotencyKey, customerId, guestName, guestEmail,
                roomTypeId, stay, roomCount, allocatedRoomIds, price, newStatus, createdAt, paymentDeadline);
    }
}
