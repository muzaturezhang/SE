package com.holidayresort.booking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.holidayresort.booking.notification.application.ReminderRepository;
import com.holidayresort.booking.notification.domain.ReminderDraft;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderSchedule;
import com.holidayresort.booking.notification.domain.ReminderStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class JdbcReminderRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-29T02:00:00Z");
    private static final LocalDate CHECK_IN = LocalDate.parse("2026-11-03");

    @Autowired
    ReminderRepository reminders;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearReminderTable() {
        jdbcTemplate.update("DELETE FROM reminder_schedule");
    }

    @Test
    void flywayMigrationCreatesTheIdempotentReminderTable() {
        ReminderSchedule first = reminders.insertIfAbsent(draft("BK-1001", "guest@example.com"));
        ReminderSchedule duplicate = reminders.insertIfAbsent(draft("BK-1001", "other@example.com"));

        assertThat(first.id()).isPositive();
        assertThat(first.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(first.version()).isZero();
        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(duplicate.recipientEmail()).isEqualTo("guest@example.com");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reminder_schedule", Integer.class)).isEqualTo(1);
    }

    @Test
    void dueQueryAndClaimSupportOneAtomicSendAttempt() {
        ReminderSchedule reminder = reminders.insertIfAbsent(draft("BK-1002", "guest@example.com"));

        assertThat(reminders.findDue(NOW, CHECK_IN, 20)).containsExactly(reminder);
        assertThat(reminders.claimForSending(reminder.id(), reminder.version(), NOW)).isTrue();
        assertThat(reminders.claimForSending(reminder.id(), reminder.version(), NOW)).isFalse();

        ReminderSchedule claimed = reminders.findById(reminder.id()).orElseThrow();
        assertThat(claimed.status()).isEqualTo(ReminderStatus.SENDING);
        assertThat(claimed.attemptCount()).isEqualTo(1);
        assertThat(claimed.version()).isEqualTo(1);

        assertThat(reminders.markSent(reminder.id(), claimed.version(), NOW.plusSeconds(30))).isTrue();
        ReminderSchedule sent = reminders.findById(reminder.id()).orElseThrow();
        assertThat(sent.status()).isEqualTo(ReminderStatus.SENT);
        assertThat(sent.sentAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(sent.attemptCount()).isEqualTo(1);
    }

    @Test
    void failureStaysPendingForRetryThenBecomesTerminal() {
        ReminderSchedule reminder = reminders.insertIfAbsent(draft("BK-1003", "guest@example.com"));
        assertThat(reminders.claimForSending(reminder.id(), reminder.version(), NOW)).isTrue();

        Instant retryAt = NOW.plusSeconds(900);
        assertThat(reminders.recordFailure(reminder.id(), 1, "provider timeout", retryAt, NOW)).isTrue();

        ReminderSchedule retrying = reminders.findById(reminder.id()).orElseThrow();
        assertThat(retrying.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(retrying.nextAttemptAt()).isEqualTo(retryAt);
        assertThat(retrying.lastError()).isEqualTo("provider timeout");
        assertThat(reminders.findDue(NOW.plusSeconds(60), CHECK_IN, 20)).isEmpty();
        assertThat(reminders.findDue(retryAt, CHECK_IN, 20)).containsExactly(retrying);

        assertThat(reminders.claimForSending(retrying.id(), retrying.version(), retryAt)).isTrue();
        assertThat(reminders.recordFailure(
                retrying.id(), retrying.version() + 1, "provider timeout", null, retryAt)).isTrue();

        ReminderSchedule failed = reminders.findById(reminder.id()).orElseThrow();
        assertThat(failed.status()).isEqualTo(ReminderStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(2);
        assertThat(failed.nextAttemptAt()).isNull();
    }

    private static ReminderDraft draft(String bookingReference, String email) {
        return new ReminderDraft(
                bookingReference,
                email,
                CHECK_IN,
                CHECK_IN.minusDays(ReminderKind.ONE_DAY.daysBefore()),
                ReminderKind.ONE_DAY,
                NOW);
    }
}
