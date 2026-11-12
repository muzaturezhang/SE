package com.holidayresort.booking.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.holidayresort.booking.booking.application.BookingException.Kind;
import com.holidayresort.booking.booking.application.port.BookingRepository;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.booking.infrastructure.InMemoryBookingRepository;
import com.holidayresort.booking.config.BookingProperties;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.member.infrastructure.InMemoryMemberRepository;
import com.holidayresort.booking.member.infrastructure.InMemoryMembershipRepository;
import com.holidayresort.booking.room.application.DefaultRoomQueryService;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class BookingServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T04:00:00Z"),
            ZoneId.of("Asia/Shanghai"));
    private InMemoryBookingRepository bookings;
    private TestInventoryEditor inventory;
    private RoomQueryService roomQuery;
    private MembershipService memberships;
    private BookingService service;
    private Customer customer;

    @BeforeEach
    void setup() {
        bookings = new InMemoryBookingRepository();
        inventory = new TestInventoryEditor();
        roomQuery = new DefaultRoomQueryService(inventory, clock);

        var members = new InMemoryMemberRepository();
        var memberR = new InMemoryMembershipRepository();
        var registration = new com.holidayresort.booking.member.application.RegistrationService(
                members, new BCryptPasswordEncoder(), clock);
        customer = registration.register("Test Guest", "guest@example.com", "safePass1");
        memberships = new MembershipService(members, memberR,
                new com.holidayresort.booking.config.MembershipProperties(68000, 5, 365), clock);

        service = new BookingService(bookings, roomQuery, inventory, memberships,
                new BookingProperties(15), clock);
    }

    @Test
    void quoteReturnsPriceWithoutDiscountForNonMember() {
        BookingService.Quote q = service.quote(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1);
        assertThat(q.price().nightlyRateMinor()).isEqualTo(98000);
        assertThat(q.price().nights()).isEqualTo(2);
        assertThat(q.price().roomCount()).isEqualTo(1);
        assertThat(q.price().discountPercent()).isEqualTo(0);
        assertThat(q.price().totalMinor()).isEqualTo(196000);
        assertThat(q.discount().active()).isFalse();
    }

    @Test
    void quoteAppliesMemberDiscount() {
        memberships.requestApplication(customer.id());
        memberships.confirmPayment(customer.id(), LocalDate.of(2026, 11, 11));
        BookingService.Quote q = service.quote(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1);
        assertThat(q.price().discountPercent()).isEqualTo(5);
        assertThat(q.price().totalMinor()).isEqualTo(186200);
    }

    @Test
    void createBookingLocksRoomsAndSetsDeadline() {
        Booking b = service.createBooking(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1,
                "Zhang San", "zhangsan@example.com", "idem-001");
        assertThat(b.reference()).startsWith("BOOK-");
        assertThat(b.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(b.allocatedRoomIds()).hasSize(1);
        assertThat(b.price().totalMinor()).isEqualTo(196000);
        assertThat(b.paymentDeadline()).isAfter(b.createdAt());

        var result = roomQuery.search(new com.holidayresort.booking.room.application.RoomSearchQuery(
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1, "courtyard"));
        assertThat(result.options().get(0).availableRooms()).isEqualTo(1);
    }

    @Test
    void createBookingIsIdempotent() {
        Booking first = service.createBooking(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1,
                "Zhang San", "zhangsan@example.com", "idem-002");
        Booking second = service.createBooking(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1,
                "Zhang San", "zhangsan@example.com", "idem-002");
        assertThat(second.reference()).isEqualTo(first.reference());
        assertThat(bookings.findAll()).hasSize(1);
    }

    @Test
    void createBookingFailsWhenNotEnoughRooms() {
        service.createBooking(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 2,
                "Zhang San", "zhang@example.com", "idem-003");
        assertThatThrownBy(() -> service.createBooking(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1,
                "Li Si", "li@example.com", "idem-004"))
                .isInstanceOfSatisfying(BookingException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.CONFLICT));
    }

    @Test
    void releaseExpiredBookingsFreesRooms() {
        Booking b = service.createBooking(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1,
                "Zhang San", "zhang@example.com", "idem-005");
        Clock expiredClock = Clock.fixed(Instant.parse("2026-11-11T04:20:00Z"),
                ZoneId.of("Asia/Shanghai"));
        BookingService expiredService = new BookingService(bookings, roomQuery, inventory,
                memberships, new BookingProperties(15), expiredClock);
        int released = expiredService.releaseExpiredBookings();
        assertThat(released).isEqualTo(1);
        Booking after = bookings.findByReference(b.reference()).orElseThrow();
        assertThat(after.status()).isEqualTo(BookingStatus.EXPIRED);
    }

    private static class TestInventoryEditor implements RoomInventoryEditor {
        private RoomInventory inventory;

        TestInventoryEditor() {
            RoomType courtyard = new RoomType("courtyard", "Courtyard Room", "Courtyard Room",
                    "Quiet courtyard room", "/img/courtyard.jpg", "Courtyard", List.of("wifi"), 98000);
            RoomType terrace = new RoomType("terrace", "Terrace Suite", "Terrace Suite",
                    "With terrace", "/img/terrace.jpg", "Terrace", List.of("wifi", "bath"), 168000);
            List<Room> rooms = List.of(
                    new Room("C101", "courtyard", true),
                    new Room("C102", "courtyard", true),
                    new Room("T201", "terrace", true));
            this.inventory = new RoomInventory(List.of(courtyard, terrace), rooms, List.of());
        }

        @Override
        public RoomInventory readSnapshot() { return inventory; }

        @Override
        public synchronized void updateSnapshot(UnaryOperator<RoomInventory> change) {
            inventory = change.apply(inventory);
        }
    }
}
