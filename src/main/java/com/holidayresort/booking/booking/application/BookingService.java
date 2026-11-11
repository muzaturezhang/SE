package com.holidayresort.booking.booking.application;

import com.holidayresort.booking.booking.application.BookingException.Kind;
import com.holidayresort.booking.booking.application.port.BookingRepository;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.booking.domain.PriceSnapshot;
import com.holidayresort.booking.config.BookingProperties;
import com.holidayresort.booking.member.application.DiscountResult;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.application.RoomSearchQuery;
import com.holidayresort.booking.room.application.RoomSearchResult;
import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomBlock;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * C-owned booking lifecycle: quoting, creation with inventory hold, idempotency,
 * expiry release and price snapshots.
 *
 * <p>Lock order: room inventory -> booking. The RoomInventoryEditor.updateSnapshot
 * callback is the single atomic boundary for allocating and releasing rooms.
 */
@Service
public class BookingService {

    private final BookingRepository bookings;
    private final RoomQueryService roomQuery;
    private final RoomInventoryEditor roomInventory;
    private final MembershipService memberships;
    private final BookingProperties properties;
    private final Clock clock;
    private final AtomicInteger sequence = new AtomicInteger(1);

    public BookingService(BookingRepository bookings,
                         RoomQueryService roomQuery,
                         RoomInventoryEditor roomInventory,
                         MembershipService memberships,
                         BookingProperties properties,
                         Clock clock) {
        this.bookings = bookings;
        this.roomQuery = roomQuery;
        this.roomInventory = roomInventory;
        this.memberships = memberships;
        this.properties = properties;
        this.clock = clock;
    }

    /** Quote a stay: check availability, apply member discount, return frozen prices. */
    public Quote quote(String customerId, String roomTypeId,
                      LocalDate arrival, LocalDate departure, int roomCount) {
        StayPeriod stay = new StayPeriod(arrival, departure);
        RoomSearchResult result = roomQuery.search(
                new RoomSearchQuery(arrival, departure, roomCount, roomTypeId));
        RoomType type = result.options().stream()
                .map(com.holidayresort.booking.room.application.RoomAvailability::roomType)
                .filter(t -> t.id().equals(roomTypeId))
                .findFirst()
                .orElseThrow(() -> new BookingException(Kind.CONFLICT, "roomType",
                        "所选日期的连续可用房间不足，请调整入住安排后重试。"));
        DiscountResult discount = memberships.discountFor(customerId);
        PriceSnapshot price = PriceSnapshot.of(type.nightlyRateMinor(), stay.nights(), roomCount,
                discount.active() ? discount.discountPercent() : 0);
        return new Quote(type, stay, roomCount, price, discount);
    }

    /**
     * Create a booking atomically: re-check availability inside the inventory
     * write boundary, allocate physical rooms, freeze the price, and hold for
     * the configured number of minutes. Idempotent by idempotencyKey.
     */
    public Booking createBooking(String customerId, String roomTypeId,
                                 LocalDate arrival, LocalDate departure, int roomCount,
                                 String guestName, String guestEmail, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = bookings.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        StayPeriod stay = new StayPeriod(arrival, departure);
        DiscountResult discount = memberships.discountFor(customerId);

        // Atomic inventory allocation: re-check and lock rooms inside one callback.
        List<String> allocated = new ArrayList<>();
        PriceSnapshot[] heldPrice = new PriceSnapshot[1];
        roomInventory.updateSnapshot(inv -> {
            List<String> availableRooms = new ArrayList<>();
            for (Room room : inv.rooms()) {
                if (!room.roomTypeId().equals(roomTypeId) || !room.enabled()) continue;
                boolean overlaps = false;
                for (RoomBlock block : inv.blocks()) {
                    if (block.roomId().equals(room.id()) && block.period().overlaps(stay)) {
                        overlaps = true; break;
                    }
                }
                if (!overlaps) availableRooms.add(room.id());
            }
            if (availableRooms.size() < roomCount) {
                throw new BookingException(Kind.CONFLICT, "rooms",
                        "所选日期的连续可用房间不足，请调整入住安排后重试。");
            }
            List<String> picked = availableRooms.subList(0, roomCount);
            allocated.addAll(picked);
            RoomType type = inv.roomTypes().stream()
                    .filter(t -> t.id().equals(roomTypeId)).findFirst().orElseThrow();
            heldPrice[0] = PriceSnapshot.of(type.nightlyRateMinor(), stay.nights(), roomCount,
                    discount.active() ? discount.discountPercent() : 0);
            List<RoomBlock> newBlocks = new ArrayList<>(inv.blocks());
            for (String roomId : picked) {
                newBlocks.add(new RoomBlock(roomId, stay));
            }
            return new RoomInventory(inv.roomTypes(), inv.rooms(), newBlocks);
        });

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime deadline = now.plusMinutes(properties.bookingHoldMinutes());
        String reference = "BOOK-" + String.format("%06d", sequence.getAndIncrement());
        Booking booking = new Booking(reference, idempotencyKey, customerId,
                guestName.strip(), guestEmail.strip(), roomTypeId, stay, roomCount,
                allocated, heldPrice[0], BookingStatus.PENDING, now, deadline);
        return bookings.save(booking);
    }

    public Booking findByReference(String reference) {
        return bookings.findByReference(reference)
                .orElseThrow(() -> new BookingException(Kind.NOT_FOUND, "reference",
                        "找不到该预订。"));
    }

    public List<Booking> listByCustomer(String customerId) {
        return bookings.findByCustomerId(customerId);
    }

    /**
     * Release all pending bookings whose payment deadline has passed.
     * Returns the number of bookings expired.
     */
    public int releaseExpiredBookings() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> pending = bookings.findAll().stream()
                .filter(b -> b.status() == BookingStatus.PENDING)
                .filter(b -> b.paymentDeadline().isBefore(now))
                .toList();
        for (Booking booking : pending) {
            roomInventory.updateSnapshot(inv -> {
                List<RoomBlock> kept = inv.blocks().stream()
                        .filter(block -> !(booking.allocatedRoomIds().contains(block.roomId())
                                && block.period().equals(booking.stay())))
                        .toList();
                return new RoomInventory(inv.roomTypes(), inv.rooms(), kept);
            });
            bookings.save(booking.withStatus(BookingStatus.EXPIRED));
        }
        return pending.size();
    }

    /** Mark a booking as paid (called by PaymentService after successful payment). */
    public Booking markPaid(String reference) {
        Booking booking = findByReference(reference);
        if (booking.status() == BookingStatus.PAID) return booking;
        return bookings.save(booking.withStatus(BookingStatus.PAID));
    }

    /** Mark a booking as failed and release its rooms (called by PaymentService). */
    public Booking markFailed(String reference) {
        Booking booking = findByReference(reference);
        if (booking.status().isTerminal()) return booking;
        roomInventory.updateSnapshot(inv -> {
            List<RoomBlock> kept = inv.blocks().stream()
                    .filter(block -> !(booking.allocatedRoomIds().contains(block.roomId())
                            && block.period().equals(booking.stay())))
                    .toList();
            return new RoomInventory(inv.roomTypes(), inv.rooms(), kept);
        });
        return bookings.save(booking.withStatus(BookingStatus.FAILED));
    }

    /** Move a booking to under review when the payment result is unknown. */
    public Booking markUnderReview(String reference) {
        Booking booking = findByReference(reference);
        return bookings.save(booking.withStatus(BookingStatus.UNDER_REVIEW));
    }

    /** Quote result projection for the UI. */
    public record Quote(RoomType roomType, StayPeriod stay, int roomCount,
                        PriceSnapshot price, DiscountResult discount) {
    }
}
