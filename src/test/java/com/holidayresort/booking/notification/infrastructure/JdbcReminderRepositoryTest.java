package com.holidayresort.booking.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.holidayresort.booking.notification.application.ReminderRepository;
import com.holidayresort.booking.notification.domain.ReminderDraft;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderSchedule;
import com.holidayresort.booking.notification.domain.ReminderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate CHECK_IN = LocalDate.parse("2026-10-30");
    private static final LocalDate CHECK_OUT = CHECK_IN.plusDays(2);

    @Autowired
    ReminderRepository reminders;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearReminderTables() {
        jdbcTemplate.update("DELETE FROM reminder_event");
        jdbcTemplate.update("DELETE FROM reminder_schedule");
    }

    @Test
    void migrationCreatesAnIdempotentRevisionAndAuditsReschedules() {
        ReminderDraft firstDraft = draft("BK-1001", "guest@example.com", CHECK_IN);
        ReminderSchedule first = reminders.upsert(firstDraft);
        ReminderSchedule duplicate = reminders.upsert(firstDraft);

        assertThat(first.id()).isPositive();
        assertThat(first.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(first.checkOutDate()).isEqualTo(CHECK_OUT);
        assertThat(first.roomTypeName()).isEqualTo("庭院客房");
        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(duplicate.version()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reminder_schedule", Integer.class)).isEqualTo(1);

        ReminderSchedule changed = reminders.upsert(
                draft("BK-1001", "other@example.com", CHECK_IN.plusDays(3)));
        assertThat(changed.id()).isEqualTo(first.id());
        assertThat(changed.recipientEmail()).isEqualTo("other@example.com");
        assertThat(changed.version()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reminder_event WHERE reminder_id = ? AND event_type = 'RESCHEDULED'",
                Integer.class, first.id())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT previous_check_in_date FROM reminder_event WHERE event_type = 'RESCHEDULED'",
                LocalDate.class)).isEqualTo(CHECK_IN);
    }

    @Test
    void dueQueryAndClaimSupportOneAtomicSendAttempt() {
        ReminderSchedule reminder = reminders.upsert(draft("BK-1002", "guest@example.com", CHECK_IN));

        assertThat(reminders.findDue(NOW, CHECK_IN, 20)).contains(reminder);
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
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reminder_event WHERE reminder_id = ?", Integer.class, reminder.id()))
                .isEqualTo(3);
    }

    @Test
    void failureStaysPendingForRetryThenBecomesTerminal() {
        ReminderSchedule reminder = reminders.upsert(draft("BK-1003", "guest@example.com", CHECK_IN));
        assertThat(reminders.claimForSending(reminder.id(), reminder.version(), NOW)).isTrue();

        Instant retryAt = NOW.plusSeconds(900);
        assertThat(reminders.recordFailure(
                reminder.id(), 1, "SMTP_SEND_FAILED", retryAt, NOW)).isTrue();

        ReminderSchedule retrying = reminders.findById(reminder.id()).orElseThrow();
        assertThat(retrying.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(retrying.nextAttemptAt()).isEqualTo(retryAt);
        assertThat(retrying.lastError()).isEqualTo("SMTP_SEND_FAILED");
        assertThat(reminders.findDue(NOW.plusSeconds(60), CHECK_IN, 20)).isEmpty();
        assertThat(reminders.findDue(retryAt, CHECK_IN, 20)).containsExactly(retrying);

        assertThat(reminders.claimForSending(retrying.id(), retrying.version(), retryAt)).isTrue();
        assertThat(reminders.recordFailure(
                retrying.id(), retrying.version() + 1, "SMTP_SEND_FAILED", null, retryAt)).isTrue();

        ReminderSchedule failed = reminders.findById(reminder.id()).orElseThrow();
        assertThat(failed.status()).isEqualTo(ReminderStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(2);
        assertThat(failed.nextAttemptAt()).isNull();
    }

    @Test
    void cancellationDropsTheDueWorkAndKeepsHistory() {
        ReminderSchedule reminder = reminders.upsert(draft("BK-1004", "guest@example.com", CHECK_IN));

        assertThat(reminders.cancelByBookingReference("BK-1004", NOW.plusSeconds(5))).isTrue();
        assertThat(reminders.cancelByBookingReference("BK-1004", NOW.plusSeconds(6))).isTrue();

        assertThat(reminders.findDue(NOW, CHECK_IN, 20)).isEmpty();
        assertThat(reminders.findById(reminder.id()).orElseThrow().status())
                .isEqualTo(ReminderStatus.CANCELLED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reminder_event WHERE reminder_id = ? AND event_type = 'CANCELLED'",
                Integer.class, reminder.id())).isEqualTo(1);
    }

    @Test
    void staleSendingRowsAreReclaimedWithoutLosingAttemptCount() {
        ReminderSchedule reminder = reminders.upsert(draft("BK-1005", "guest@example.com", CHECK_IN));
        assertThat(reminders.claimForSending(reminder.id(), reminder.version(), NOW)).isTrue();
        jdbcTemplate.update("""
                UPDATE reminder_schedule SET last_attempt_at = ? WHERE id = ?
                """, java.sql.Timestamp.from(NOW.minusSeconds(3600)), reminder.id());
        assertThat(reminders.recoverStaleSending(NOW.minusSeconds(1), NOW, 3)).isEqualTo(1);
        ReminderSchedule recovered = reminders.findById(reminder.id()).orElseThrow();
        assertThat(recovered.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(recovered.attemptCount()).isEqualTo(1);
        assertThat(recovered.lastError()).isEqualTo("DELIVERY_LEASE_EXPIRED");
    }

    private static ReminderDraft draft(String reference, String email, LocalDate checkIn) {
        LocalDate remindOn = checkIn.minusDays(ReminderKind.ONE_DAY.daysBefore());
        return new ReminderDraft(
                reference,
                email,
                checkIn,
                checkIn.plusDays(2),
                "庭院客房",
                remindOn,
                ReminderKind.ONE_DAY,
                remindOn.atTime(9, 0).atZone(ZONE).toInstant(),
                NOW);
    }
}
