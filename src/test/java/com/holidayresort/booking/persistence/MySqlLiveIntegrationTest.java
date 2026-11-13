package com.holidayresort.booking.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.member.application.RegistrationService;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.notification.application.ReminderRepository;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderStatus;
import com.holidayresort.booking.payment.application.PaymentService;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import com.holidayresort.booking.payment.infrastructure.SandboxPaymentProvider;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".+")
@Sql(scripts = "/sql/clean-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MySqlLiveIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    RegistrationService registration;
    @Autowired
    BookingService bookings;
    @Autowired
    PaymentService payments;
    @Autowired
    SandboxPaymentProvider sandbox;
    @Autowired
    ReminderRepository reminders;
    @Autowired
    Clock clock;

    @Test
    void mysqlEightAppliesMigrationsAndSeedCatalogue() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isGreaterThanOrEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_type", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room", Integer.class))
                .isEqualTo(8);
    }

    @Test
    void mysqlEightExercisesBookingPaymentReminderAndReleaseLifecycle() {
        Customer customer = registration.register("Live Guest", "live-guest@example.com", "safePass1");
        LocalDate arrival = LocalDate.now(clock).plusDays(20);

        Booking booking = bookings.createBooking(customer.id(), "courtyard", arrival, arrival.plusDays(2),
                1, "Live Guest", "live-guest@example.com", "live-idem-1");
        Booking duplicate = bookings.createBooking(customer.id(), "courtyard", arrival, arrival.plusDays(2),
                1, "Live Guest", "live-guest@example.com", "live-idem-1");

        assertThat(duplicate.reference()).isEqualTo(booking.reference());
        assertThat(reminders.findByBookingReferenceAndKind(booking.reference(), ReminderKind.ONE_DAY))
                .hasValueSatisfying(reminder -> assertThat(reminder.status()).isEqualTo(ReminderStatus.PENDING));

        var payment = payments.initiatePayment(booking.reference(), customer.id());
        sandbox.simulateSuccess(payment.providerReference());
        var paid = payments.handleCallback(payment.providerReference());
        var repeated = payments.handleCallback(payment.providerReference());

        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(repeated.id()).isEqualTo(paid.id());
        assertThat(bookings.findByReference(booking.reference()).status()).isEqualTo(BookingStatus.PAID);

        LocalDate later = LocalDate.now(clock).plusDays(30);
        Booking held = bookings.createBooking(customer.id(), "courtyard", later, later.plusDays(2), 4,
                "Live Guest", "live-guest@example.com", "live-idem-2");
        bookings.markFailed(held.reference());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM booking_room_night_history WHERE booking_reference = ?
                """, Integer.class, held.reference())).isEqualTo(8);

        Booking resold = bookings.createBooking(customer.id(), "courtyard", later, later.plusDays(2), 1,
                "Live Guest", "live-guest@example.com", "live-idem-3");
        assertThat(resold.status()).isEqualTo(BookingStatus.PENDING);
    }
}
