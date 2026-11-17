package com.holidayresort.booking.booking.application;

import com.holidayresort.booking.booking.application.BookingException.Kind;
import com.holidayresort.booking.booking.application.port.BookingInventoryPort;
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
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-owned booking lifecycle: quoting, creation with inventory hold, idempotency,
 * expiry release and price snapshots.
 *
 * <p>The inventory port owns the atomic allocation boundary. D implements it
 * with the shared room-inventory -> booking lock order, while dev/test use an
 * in-memory implementation.
 */
@Service
public class BookingService {

    private final BookingRepository bookings;
    private final RoomQueryService roomQuery;
    private final BookingInventoryPort inventory;
    private final MembershipService memberships;
    private final BookingProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    /**
     * Compatibility constructor retained for focused C tests and callers that
     * still supply B's room inventory editor directly.
     */
    public BookingService(BookingRepository bookings,
                          RoomQueryService roomQuery,
                          BookingInventoryPort inventory,
                          MembershipService memberships,
                          BookingProperties properties,
                          Clock clock,
                          ApplicationEventPublisher events) {
        this.bookings = bookings;
        this.roomQuery = roomQuery;
        this.inventory = inventory;
        this.memberships = memberships;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
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
                        "Not enough rooms available for the selected dates. Please adjust your stay."));
        DiscountResult discount = memberships.discountFor(customerId);
        PriceSnapshot price = PriceSnapshot.of(type.nightlyRateMinor(), stay.nights(), roomCount,
                discount.active() ? discount.discountPercent() : 0);
        return new Quote(type, stay, roomCount, price, discount);
    }

    /**
     * Create a booking atomically: re-check availability inside the inventory
     * write boundary, allocate physical rooms, freeze the price, and hold for
     * the configured minutes. A client retry with the same key returns the
     * original booking.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(String customerId, String roomTypeId,
                                 LocalDate arrival, LocalDate departure, int roomCount,
                                 String guestName, String guestEmail, String idempotencyKey) {
        String normalizedKey = idempotencyKey == null || idempotencyKey.isBlank()
                ? null : idempotencyKey.strip();
        if (normalizedKey != null) {
            var existing = bookings.findByIdempotencyKey(normalizedKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        StayPeriod stay = new StayPeriod(arrival, departure);
        DiscountResult discount = memberships.discountFor(customerId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime deadline = now.plusMinutes(properties.bookingHoldMinutes());
        String reference = "BOOK-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();

        Booking booking = inventory.allocate(roomTypeId, stay, roomCount, (type, roomIds) -> {
            PriceSnapshot price = PriceSnapshot.of(type.nightlyRateMinor(), stay.nights(), roomCount,
                    discount.active() ? discount.discountPercent() : 0);
            return bookings.save(new Booking(reference, normalizedKey, customerId,
                    guestName.strip(), guestEmail.strip(), roomTypeId, stay, roomCount,
                    roomIds, price, BookingStatus.PENDING, now, deadline));
        });

        String roomTypeName = roomQuery.listRoomTypes().stream()
                .filter(type -> type.id().equals(roomTypeId))
                .map(RoomType::name)
                .findFirst()
                .orElse(roomTypeId);
        events.publishEvent(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.CREATED, booking, roomTypeName));
        return booking;
    }

    public Booking findByReference(String reference) {
        return bookings.findByReference(reference)
                .orElseThrow(() -> new BookingException(Kind.NOT_FOUND, "reference",
                        "Booking not found."));
    }

    public List<Booking> listByCustomer(String customerId) {
        return bookings.findByCustomerId(customerId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int releaseExpiredBookings() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Booking> pending = bookings.findAll().stream()
                .filter(b -> b.status() == BookingStatus.PENDING)
                .filter(b -> b.paymentDeadline().isBefore(now))
                .toList();
        for (Booking booking : pending) {
            inventory.release(booking, "HOLD_EXPIRED");
            Booking expired = bookings.save(booking.withStatus(BookingStatus.EXPIRED));
            events.publishEvent(new BookingLifecycleEvent(
                    BookingLifecycleEvent.Type.STATUS_CHANGED, expired, null));
        }
        return pending.size();
    }

    /** Cancel an owned booking before check-in and release its inventory. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking cancel(String customerId, String reference) {
        Booking booking = findOwned(customerId, reference);
        if (booking.status() == BookingStatus.CANCELLED) {
            return booking;
        }
        if (booking.status() != BookingStatus.PENDING && booking.status() != BookingStatus.PAID) {
            throw new BookingException(Kind.CONFLICT, "status",
                    "This booking can no longer be cancelled.");
        }
        if (!booking.stay().arrival().isAfter(LocalDate.now(clock))) {
            throw new BookingException(Kind.CONFLICT, "arrival",
                    "A booking cannot be cancelled after its arrival date.");
        }
        inventory.release(booking, "CUSTOMER_CANCELLED");
        Booking cancelled = bookings.save(booking.withStatus(BookingStatus.CANCELLED));
        events.publishEvent(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.STATUS_CHANGED, cancelled, null));
        return cancelled;
    }

    /**
     * Change an owned PENDING booking, or a PAID booking whose new total is
     * unchanged. A price-changing PAID booking must be cancelled/rebooked so a
     * second charge is never created silently.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking change(String customerId, String reference, String roomTypeId,
                          LocalDate arrival, LocalDate departure, int roomCount) {
        Booking booking = findOwned(customerId, reference);
        if (booking.status() != BookingStatus.PENDING && booking.status() != BookingStatus.PAID) {
            throw new BookingException(Kind.CONFLICT, "status",
                    "Only a pending or paid booking can be changed.");
        }
        if (!booking.stay().arrival().isAfter(LocalDate.now(clock))) {
            throw new BookingException(Kind.CONFLICT, "arrival",
                    "A booking cannot be changed after its arrival date.");
        }
        if (roomCount <= 0) {
            throw new BookingException(Kind.INVALID, "roomCount", "Room count must be positive.");
        }
        StayPeriod newStay = new StayPeriod(arrival, departure);
        RoomType newType = roomQuery.listRoomTypes().stream()
                .filter(type -> type.id().equals(roomTypeId))
                .findFirst()
                .orElseThrow(() -> new BookingException(Kind.INVALID, "roomType", "Unknown room type."));
        DiscountResult discount = memberships.discountFor(customerId);
        PriceSnapshot newPrice = PriceSnapshot.of(newType.nightlyRateMinor(), newStay.nights(),
                roomCount, discount.active() ? discount.discountPercent() : 0);
        if (booking.status() == BookingStatus.PAID
                && newPrice.totalMinor() != booking.price().totalMinor()) {
            throw new BookingException(Kind.CONFLICT, "price",
                    "A paid booking can only be changed when the total price is unchanged.");
        }
        LocalDateTime newDeadline = booking.status() == BookingStatus.PENDING
                ? LocalDateTime.now(clock).plusMinutes(properties.bookingHoldMinutes())
                : booking.paymentDeadline();
        Booking changed = inventory.change(booking, roomTypeId, newStay, roomCount,
                (type, roomIds) -> bookings.save(booking.withChange(
                        roomTypeId, newStay, roomCount, roomIds, newPrice,
                        booking.status(), newDeadline)));
        events.publishEvent(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.CHANGED, changed, newType.name()));
        return changed;
    }

    private Booking findOwned(String customerId, String reference) {
        Booking booking = findByReference(reference);
        if (!booking.customerId().equals(customerId)) {
            throw new BookingException(Kind.NOT_FOUND, "reference", "Booking not found.");
        }
        return booking;
    }
    /** Mark a booking as paid (called by PaymentService after successful payment). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking markPaid(String reference) {
        Booking booking = findByReference(reference);
        if (booking.status() == BookingStatus.PAID) {
            return booking;
        }
        if (booking.status().isTerminal()) {
            throw new BookingException(Kind.CONFLICT, "status",
                    "A failed or expired booking cannot be marked paid.");
        }
        Booking paid = bookings.save(booking.withStatus(BookingStatus.PAID));
        events.publishEvent(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.STATUS_CHANGED, paid, null));
        return paid;
    }

    /** Mark a booking as failed and release its rooms (called by PaymentService). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking markFailed(String reference) {
        Booking booking = findByReference(reference);
        if (booking.status().isTerminal()) {
            return booking;
        }
        inventory.release(booking, "PAYMENT_FAILED");
        Booking failed = bookings.save(booking.withStatus(BookingStatus.FAILED));
        events.publishEvent(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.STATUS_CHANGED, failed, null));
        return failed;
    }

    /** Move a booking to under review when the payment result is unknown. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking markUnderReview(String reference) {
        Booking booking = findByReference(reference);
        if (booking.status().isTerminal()) {
            return booking;
        }
        Booking underReview = bookings.save(booking.withStatus(BookingStatus.UNDER_REVIEW));
        events.publishEvent(new BookingLifecycleEvent(
                BookingLifecycleEvent.Type.STATUS_CHANGED, underReview, null));
        return underReview;
    }

    /** Quote result projection for the UI. */
    public record Quote(RoomType roomType, StayPeriod stay, int roomCount,
                        PriceSnapshot price, DiscountResult discount) {
    }
}
