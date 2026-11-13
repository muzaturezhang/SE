package com.holidayresort.booking.notification.application;

import com.holidayresort.booking.notification.config.NotificationProperties;
import com.holidayresort.booking.notification.domain.ReminderDraft;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderSchedule;
import com.holidayresort.booking.notification.email.EmailDeliveryException;
import com.holidayresort.booking.notification.email.EmailTransport;
import com.holidayresort.booking.notification.email.ReminderEmailComposer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Schedules one-day reminders and dispatches due work.
 *
 * <p>The reminder row is the outbox entry: it is committed before an email is attempted. Email
 * delivery is intentionally outside a database transaction. Delivery claim and terminal updates
 * are short, version-checked statements so a slow provider never holds database locks.
 */
@Service
public class ReminderService implements NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReminderService.class);
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_BOOKING_REFERENCE_LENGTH = 64;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_ROOM_TYPE_LENGTH = 80;
    private static final int MAX_ERROR_CODE_LENGTH = 80;

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

    @Override
    public ReminderSchedule scheduleOneDayReminder(ReminderRequest request) {
        if (request == null) {
            throw invalid("request", "提醒请求不能为空");
        }
        String bookingReference = normalizeBookingReference(request.bookingReference());
        String recipientEmail = normalizeEmail(request.recipientEmail());
        String roomTypeName = normalizeRoomType(request.roomTypeName());
        LocalDate checkInDate = request.checkInDate();
        LocalDate checkOutDate = request.checkOutDate();
        validateStay(checkInDate, checkOutDate);

        LocalDate remindOnDate = checkInDate.minusDays(ReminderKind.ONE_DAY.daysBefore());
        Instant scheduledAt = remindOnDate
                .atTime(properties.sendHour(), 0)
                .atZone(clock.getZone())
                .toInstant();
        ReminderDraft draft = new ReminderDraft(
                bookingReference,
                recipientEmail,
                checkInDate,
                checkOutDate,
                roomTypeName,
                remindOnDate,
                ReminderKind.ONE_DAY,
                scheduledAt,
                clock.instant());
        return reminders.upsert(draft);
    }

    @Override
    public boolean cancelOneDayReminder(String bookingReference) {
        String normalized = normalizeBookingReference(bookingReference);
        boolean existed = reminders.cancelByBookingReference(normalized, clock.instant());
        LOG.info("Reminder cancellation processed: existed={}", existed);
        return existed;
    }

    @Override
    public DispatchResult dispatchDue() {
        Instant firstNow = clock.instant();
        List<ReminderSchedule> due = reminders.findDue(firstNow, LocalDate.now(clock), properties.batchSize());
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
                    LOG.info("Reminder send completed after state changed: reminderId={}", reminder.id());
                }
            } catch (RuntimeException exception) {
                boolean terminal = reminder.attemptCount() + 1 >= properties.maxAttempts();
                Instant updatedAt = clock.instant();
                Instant nextAttemptAt = terminal ? null : updatedAt.plus(properties.retryDelay());
                String errorCode = safeErrorCode(exception);
                boolean recorded = reminders.recordFailure(
                        reminder.id(),
                        claimedVersion,
                        errorCode,
                        nextAttemptAt,
                        updatedAt);
                if (!recorded) {
                    skipped++;
                    LOG.info("Reminder failure followed a state change: reminderId={}", reminder.id());
                } else if (terminal) {
                    failed++;
                    LOG.error("Reminder delivery failed terminally: reminderId={} attempts={} code={}",
                            reminder.id(), reminder.attemptCount() + 1, errorCode);
                } else {
                    retrying++;
                    LOG.warn("Reminder delivery will retry: reminderId={} attempts={} code={}",
                            reminder.id(), reminder.attemptCount() + 1, errorCode);
                }
            }
        }
        DispatchResult result = new DispatchResult(due.size(), sent, retrying, failed, skipped);
        LOG.info("Reminder dispatch finished: candidates={} sent={} retrying={} failed={} skipped={}",
                result.candidates(), result.sent(), result.retrying(), result.failed(), result.skipped());
        return result;
    }

    private void validateStay(LocalDate checkInDate, LocalDate checkOutDate) {
        if (checkInDate == null) {
            throw invalid("checkInDate", "入住日期不能为空");
        }
        if (checkOutDate == null) {
            throw invalid("checkOutDate", "退房日期不能为空");
        }
        if (!checkOutDate.isAfter(checkInDate)) {
            throw invalid("checkOutDate", "退房日期必须晚于入住日期");
        }
        if (!checkInDate.isAfter(LocalDate.now(clock))) {
            throw invalid("checkInDate", "入住日期必须晚于今天才能安排一天前提醒");
        }
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

    private static String normalizeRoomType(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("roomTypeName", "房型不能为空");
        }
        String normalized = value.strip();
        if (normalized.length() > MAX_ROOM_TYPE_LENGTH) {
            throw invalid("roomTypeName", "房型名称不能超过80个字符");
        }
        if (normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid("roomTypeName", "房型名称包含不支持的控制字符");
        }
        return normalized;
    }

    private static NotificationException invalid(String field, String message) {
        return new NotificationException(NotificationException.Kind.INVALID_REQUEST, field, message);
    }

    /**
     * Only a stable error category is persisted or logged. Provider messages may contain an email
     * address or other personal data, so they never cross this boundary.
     */
    private static String safeErrorCode(RuntimeException exception) {
        String code = exception instanceof EmailDeliveryException delivery
                ? delivery.deliveryCode()
                : exception.getClass().getSimpleName();
        if (code == null || code.isBlank()) {
            code = "EMAIL_DELIVERY_FAILED";
        }
        code = code.replaceAll("[^A-Za-z0-9_.-]", "_");
        return code.length() <= MAX_ERROR_CODE_LENGTH ? code : code.substring(0, MAX_ERROR_CODE_LENGTH);
    }
}