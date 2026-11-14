package com.holidayresort.booking.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.member.application.RegistrationService;
import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.application.port.MembershipRepository;
import com.holidayresort.booking.member.application.port.StaffDirectory;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.notification.application.ReminderRepository;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderStatus;
import com.holidayresort.booking.payment.application.PaymentService;
import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import com.holidayresort.booking.payment.infrastructure.SandboxPaymentProvider;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mysql-profile-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.locations=classpath:db/migration"
})
@ActiveProfiles("mysql")
@Sql(scripts = "/sql/clean-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MySqlProfileIntegrationTest {

    @Autowired
    RegistrationService registration;
    @Autowired
    BookingService bookings;
    @Autowired
    ReminderRepository reminders;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    Clock clock;
    @Autowired
    MemberRepository members;
    @Autowired
    MembershipRepository memberships;
    @Autowired
    MembershipService membershipService;
    @Autowired
    StaffDirectory staffDirectory;
    @Autowired
    PaymentService payments;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    SandboxPaymentProvider sandbox;

    @Test
    void mysqlProfileWiresJdbcPortsAndBookingReminderLifecycle() {
        Customer customer = registration.register("Mysql Guest", "mysql-guest@example.com", "safePass1");
        LocalDate arrival = LocalDate.now(clock).plusDays(3);
        LocalDate departure = arrival.plusDays(2);

        Booking booking = bookings.createBooking(customer.id(), "courtyard", arrival, departure, 1,
                "Mysql Guest", "mysql-guest@example.com", "idem-mysql-1");
        Booking duplicate = bookings.createBooking(customer.id(), "courtyard", arrival, departure, 1,
                "Mysql Guest", "mysql-guest@example.com", "idem-mysql-1");

        assertThat(duplicate.reference()).isEqualTo(booking.reference());
        assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.allocatedRoomIds()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_room_night WHERE booking_reference = ?
                """, Integer.class, booking.reference())).isEqualTo(2);
        assertThat(reminders.findByBookingReferenceAndKind(booking.reference(), ReminderKind.ONE_DAY))
                .hasValueSatisfying(reminder -> assertThat(reminder.status()).isEqualTo(ReminderStatus.PENDING));

        bookings.markFailed(booking.reference());

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_room_night WHERE booking_reference = ?
                """, Integer.class, booking.reference())).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_room_night_history WHERE booking_reference = ?
                """, Integer.class, booking.reference())).isEqualTo(2);
        assertThat(reminders.findByBookingReferenceAndKind(booking.reference(), ReminderKind.ONE_DAY))
                .hasValueSatisfying(reminder -> assertThat(reminder.status()).isEqualTo(ReminderStatus.CANCELLED));
    }

    @Test
    void releasedInventoryCanBeSoldAgainWhileHistoryRemains() {
        Customer customer = registration.register("Release Guest", "release@example.com", "safePass1");
        LocalDate arrival = LocalDate.now(clock).plusDays(7);
        Booking held = bookings.createBooking(customer.id(), "courtyard", arrival, arrival.plusDays(2), 4,
                "Release Guest", "release@example.com", "idem-release-1");

        assertThatThrownBy(() -> bookings.createBooking(customer.id(), "courtyard", arrival,
                arrival.plusDays(2), 1, "Other Guest", "other@example.com", "idem-release-2"))
                .isInstanceOf(com.holidayresort.booking.booking.application.BookingException.class);

        bookings.markFailed(held.reference());
        Booking resold = bookings.createBooking(customer.id(), "courtyard", arrival, arrival.plusDays(2), 1,
                "Release Guest", "release@example.com", "idem-release-3");

        assertThat(resold.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_room_night WHERE booking_reference = ?
                """, Integer.class, resold.reference())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_room_night_history WHERE booking_reference = ?
                """, Integer.class, held.reference())).isEqualTo(8);
    }
    @Test
    void membershipFeePaymentPersistsSeparatePurposeAndActivatesMembership() {
        Customer customer = registration.register("Member Payment", "member-payment@example.com", "safePass1");

        var payment = payments.initiateMembershipPayment(customer.id());
        sandbox.simulateSuccess(payment.providerReference());
        var paid = payments.handleCallback(payment.providerReference());

        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.purpose()).isEqualTo(com.holidayresort.booking.payment.domain.PaymentPurpose.MEMBERSHIP);
        assertThat(paid.bookingReference()).isNull();
        assertThat(paymentRepository.findByCustomerIdAndPurpose(customer.id(),
                com.holidayresort.booking.payment.domain.PaymentPurpose.MEMBERSHIP))
                .hasValueSatisfying(saved -> assertThat(saved.status()).isEqualTo(PaymentStatus.PAID));
        assertThat(membershipService.statusFor(customer.id()))
                .hasValueSatisfying(membership -> assertThat(membership.paymentConfirmed()).isTrue());
    }
    @Test
    void bookingChangeAndCancellationUpdateInventoryAndReminder() {
        Customer customer = registration.register("Change Guest", "change@example.com", "safePass1");
        LocalDate arrival = LocalDate.now(clock).plusDays(12);
        Booking booking = bookings.createBooking(customer.id(), "courtyard", arrival, arrival.plusDays(2),
                1, "Change Guest", "change@example.com", "idem-change-1");

        Booking changed = bookings.change(customer.id(), booking.reference(), "courtyard",
                arrival.plusDays(1), arrival.plusDays(3), 2);

        assertThat(changed.reference()).isEqualTo(booking.reference());
        assertThat(changed.roomCount()).isEqualTo(2);
        assertThat(changed.allocatedRoomIds()).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_room_night WHERE booking_reference = ? AND stay_date >= ? AND stay_date < ?
                """, Integer.class, booking.reference(), java.sql.Date.valueOf(arrival.plusDays(1)),
                java.sql.Date.valueOf(arrival.plusDays(3)))).isEqualTo(4);
        assertThat(reminders.findByBookingReferenceAndKind(booking.reference(), ReminderKind.ONE_DAY))
                .hasValueSatisfying(reminder -> assertThat(reminder.checkInDate()).isEqualTo(arrival.plusDays(1)));

        Booking cancelled = bookings.cancel(customer.id(), booking.reference());

        assertThat(cancelled.status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_room_night WHERE booking_reference = ?
                """, Integer.class, booking.reference())).isZero();
        assertThat(reminders.findByBookingReferenceAndKind(booking.reference(), ReminderKind.ONE_DAY))
                .hasValueSatisfying(reminder -> assertThat(reminder.status()).isEqualTo(ReminderStatus.CANCELLED));
    }
    @Test
    void adaptersPersistCustomerMembershipStaffAndPayment() {
        Customer customer = registration.register("Adapter Guest", "adapter@example.com", "safePass1");
        assertThat(members.findByEmail("ADAPTER@EXAMPLE.COM")).contains(customer);

        membershipService.requestApplication(customer.id());
        membershipService.confirmPayment(customer.id(), LocalDate.now(clock));
        assertThat(memberships.findByCustomerId(customer.id()))
                .hasValueSatisfying(membership -> assertThat(membership.paymentConfirmed()).isTrue());

        staffDirectory.add(new StaffDirectory.StaffRecord("mysql-staff", "bcrypt-hash"));
        assertThat(staffDirectory.findByUsername("mysql-staff")).isPresent();

        LocalDate arrival = LocalDate.now(clock).plusDays(5);
        Booking booking = bookings.createBooking(customer.id(), "courtyard", arrival, arrival.plusDays(1),
                1, "Adapter Guest", "adapter@example.com", "idem-payment-1");
        var payment = payments.initiatePayment(booking.reference(), customer.id());
        var duplicatePayment = payments.initiatePayment(booking.reference(), customer.id());
        assertThat(duplicatePayment.id()).isEqualTo(payment.id());
        sandbox.simulateSuccess(payment.providerReference());
        var paid = payments.handleCallback(payment.providerReference());
        var duplicateCallback = payments.handleCallback(payment.providerReference());

        assertThat(duplicateCallback.id()).isEqualTo(paid.id());
        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentRepository.findByBookingReference(booking.reference()))
                .hasValueSatisfying(saved -> assertThat(saved.status()).isEqualTo(PaymentStatus.PAID));
        assertThat(bookings.findByReference(booking.reference()).status()).isEqualTo(BookingStatus.PAID);
    }
}
