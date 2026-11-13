package com.holidayresort.booking.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.booking.infrastructure.InMemoryBookingRepository;
import com.holidayresort.booking.config.BookingProperties;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.member.infrastructure.InMemoryMemberRepository;
import com.holidayresort.booking.member.infrastructure.InMemoryMembershipRepository;
import com.holidayresort.booking.payment.application.port.PaymentProvider;
import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import com.holidayresort.booking.payment.infrastructure.InMemoryPaymentRepository;
import com.holidayresort.booking.payment.infrastructure.SandboxPaymentProvider;
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

class PaymentServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T04:00:00Z"),
            ZoneId.of("Asia/Shanghai"));
    private InMemoryPaymentRepository payments;
    private SandboxPaymentProvider provider;
    private BookingService bookings;
    private PaymentService service;
    private Customer customer;
    private Booking booking;

    @BeforeEach
    void setup() {
        payments = new InMemoryPaymentRepository();
        provider = new SandboxPaymentProvider();

        InMemoryBookingRepository bookingRepo = new InMemoryBookingRepository();
        TestInventoryEditor inventory = new TestInventoryEditor();
        RoomQueryService roomQuery = new DefaultRoomQueryService(inventory, clock);

        var members = new InMemoryMemberRepository();
        var memberR = new InMemoryMembershipRepository();
        var registration = new com.holidayresort.booking.member.application.RegistrationService(
                members, new BCryptPasswordEncoder(), clock);
        customer = registration.register("Test Guest", "g@example.com", "safePass1");
        MembershipService memberships = new MembershipService(members, memberR,
                new com.holidayresort.booking.config.MembershipProperties(68000, 5, 365), clock);

        bookings = new BookingService(bookingRepo, roomQuery, inventory, memberships,
                new BookingProperties(15), clock);
        booking = bookings.createBooking(customer.id(), "courtyard",
                LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 17), 1,
                "Zhang San", "zhang@example.com", "idem-pay-1");

        service = new PaymentService(payments, provider, bookings, clock);
    }

    @Test
    void initiateCreatesPendingPayment() {
        Payment p = service.initiatePayment(booking.reference(), customer.id());
        assertThat(p.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.amountMinor()).isEqualTo(196000);
        assertThat(p.providerReference()).startsWith("SBX-");
    }

    @Test
    void initiateIsIdempotentForSameBooking() {
        Payment first = service.initiatePayment(booking.reference(), customer.id());
        Payment second = service.initiatePayment(booking.reference(), customer.id());
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(payments.findAll()).hasSize(1);
    }

    @Test
    void successfulCallbackMarksBothPaid() {
        Payment p = service.initiatePayment(booking.reference(), customer.id());
        provider.simulateSuccess(p.providerReference());
        Payment result = service.handleCallback(p.providerReference());
        assertThat(result.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(bookings.findByReference(booking.reference()).status())
                .isEqualTo(BookingStatus.PAID);
    }

    @Test
    void failedCallbackMarksFailedAndReleasesRooms() {
        Payment p = service.initiatePayment(booking.reference(), customer.id());
        provider.simulateFailure(p.providerReference(), "insufficient balance");
        Payment result = service.handleCallback(p.providerReference());
        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(bookings.findByReference(booking.reference()).status())
                .isEqualTo(BookingStatus.FAILED);
    }

    @Test
    void unknownCallbackGoesToUnderReview() {
        Payment p = service.initiatePayment(booking.reference(), customer.id());
        provider.simulateUnknown(p.providerReference());
        Payment result = service.handleCallback(p.providerReference());
        assertThat(result.status()).isEqualTo(PaymentStatus.UNDER_REVIEW);
        assertThat(bookings.findByReference(booking.reference()).status())
                .isEqualTo(BookingStatus.UNDER_REVIEW);
    }

    @Test
    void staffCanConfirmUnderReviewPayment() {
        Payment p = service.initiatePayment(booking.reference(), customer.id());
        provider.simulateUnknown(p.providerReference());
        service.handleCallback(p.providerReference());
        Payment confirmed = service.staffConfirmPaid(p.providerReference());
        assertThat(confirmed.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(bookings.findByReference(booking.reference()).status())
                .isEqualTo(BookingStatus.PAID);
    }

    @Test
    void paidBookingCannotInitiateNewPayment() {
        Payment p = service.initiatePayment(booking.reference(), customer.id());
        provider.simulateSuccess(p.providerReference());
        service.handleCallback(p.providerReference());
        assertThatThrownBy(() -> service.initiatePayment(booking.reference(), customer.id()))
                .isInstanceOf(PaymentException.class);
    }

    private static class TestInventoryEditor implements RoomInventoryEditor {
        private RoomInventory inventory;
        TestInventoryEditor() {
            RoomType courtyard = new RoomType("courtyard", "Courtyard Room", "Courtyard",
                    "Quiet", "/img/c.jpg", "Courtyard", List.of("wifi"), 98000);
            RoomType terrace = new RoomType("terrace", "Terrace Suite", "Terrace",
                    "With terrace", "/img/t.jpg", "Terrace", List.of("wifi"), 168000);
            List<Room> rooms = List.of(
                    new Room("C101", "courtyard", true),
                    new Room("C102", "courtyard", true),
                    new Room("T201", "terrace", true));
            inventory = new RoomInventory(List.of(courtyard, terrace), rooms, List.of());
        }
        public RoomInventory readSnapshot() { return inventory; }
        public synchronized void updateSnapshot(UnaryOperator<RoomInventory> c) { inventory = c.apply(inventory); }
    }
}
