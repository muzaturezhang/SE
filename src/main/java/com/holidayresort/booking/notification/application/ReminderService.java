package com.holidayresort.booking.notification.application;

import com.holidayresort.booking.notification.config.NotificationProperties;
import com.holidayresort.booking.notification.domain.ReminderDraft;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderSchedule;
import com.holidayresort.booking.notification.email.EmailTransport;
import com.holidayresort.booking.notification.email.ReminderEmailComposer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Schedules the one-day reminder and dispatches due work.
 *
 * <p>Email delivery is intentionally outside a database transaction. The repository claim and
 * terminal state updates are short atomic statements so a failed provider call never holds locks.
 */
@Service
public class ReminderService {

    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_BOOKING_REFERENCE_LENGTH = 64;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_ERROR_LENGTH = 500;

    private final ReminderRepository reminders;
    private final EmailTransport emailTransport;
    private final ReminderEmailComposer composer;
    private final Clock clock;
    private final NotificationProperties properties;

    public ReminderService(
            ReminderRepository reminders,
            EmailTransport emailTransport,
            ReminderEmailComposer composer,
            Clock clock,
            NotificationProperties properties) {
        this.reminders = Objects.requireNonNull(reminders, "reminders must not be null");
        this.emailTransport = Objects.requireNonNull(emailTransport, "emailTransport must not be null");
        this.composer = Objects.requireNonNull(composer, "composer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public ReminderSchedule scheduleOneDayReminder(ReminderRequest request) {
        if (request == null) {
            throw invalid("request", "提醒请求不能为空");
        }
        String bookingReference = normalizeBookingReference(request.bookingReference());
        String recipientEmail = normalizeEmail(request.recipientEmail());
        LocalDate checkInDate = request.checkInDate();
        if (checkInDate == null) {
            throw invalid("checkInDate", "入住日期不能为空");
        }
        LocalDate today = LocalDate.now(clock);
        if (!checkInDate.isAfter(today)) {
            throw invalid("checkInDate", "入住日期必须晚于今天才能安排一天前提醒");
        }

        Instant now = clock.instant();
        ReminderDraft draft = new ReminderDraft(
                bookingReference,
                recipientEmail,
                checkInDate,
                checkInDate.minusDays(ReminderKind.ONE_DAY.daysBefore()),
                ReminderKind.ONE_DAY,
                now);
        ReminderSchedule stored = reminders.insertIfAbsent(draft);
        if (!stored.recipientEmail().equals(recipientEmail) || !stored.checkInDate().equals(checkInDate)) {
            throw new NotificationException(
                    NotificationException.Kind.CONFLICT,
                    "bookingReference",
                    "同一预订的提醒内容已存在且与本次请求不一致，请先核对原调度记录");
        }
        return stored;
    }

    public DispatchResult dispatchDue() {
        Instant firstNow = clock.instant();
        List<ReminderSchedule> due = reminders.findDue(
                firstNow, LocalDate.now(clock), properties.batchSize());
        int sent = 0;
        int retrying = 0;
        int failed = 0;
        int skipped = 0;

        for (ReminderSchedule reminder : due) {
            Instant claimTime = clock.instant();
            if (!reminders.claimForSending(reminder.id(), reminder.version(), claimTime)) {
                skipped++;
                continue;
            }
            long claimedVersion = reminder.version() + 1;
            try {
                emailTransport.send(composer.compose(reminder));
                Instant sentAt = clock.instant();
                if (reminders.markSent(reminder.id(), claimedVersion, sentAt)) {
                    sent++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                boolean terminal = reminder.attemptCount() + 1 >= properties.maxAttempts();
                Instant updatedAt = clock.instant();
                Instant nextAttemptAt = terminal ? null : updatedAt.plus(properties.retryDelay());
                boolean recorded = reminders.recordFailure(
                        reminder.id(),
                        claimedVersion,
                        safeError(exception),
                        nextAttemptAt,
                        updatedAt);
                if (!recorded) {
                    skipped++;
                } else if (terminal) {
                    failed++;
                } else {
                    retrying++;
                }
            }
        }
        return new DispatchResult(due.size(), sent, retrying, failed, skipped);
    }

    private static String normalizeBookingReference(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("bookingReference", "预订编号不能为空");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_BOOKING_REFERENCE_LENGTH) {
            throw invalid("bookingReference", "预订编号不能超过64个字符");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid("bookingReference", "预订编号包含不支持的控制字符");
        }
        return normalized;
    }

    private static String normalizeEmail(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("recipientEmail", "收件邮箱不能为空");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_EMAIL_LENGTH || !SIMPLE_EMAIL.matcher(normalized).matches()) {
            throw invalid("recipientEmail", "收件邮箱格式不正确");
        }
        return normalized;
    }

    private static NotificationException invalid(String field, String message) {
        return new NotificationException(NotificationException.Kind.INVALID_REQUEST, field, message);
    }

    private static String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
