package com.holidayresort.booking.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.holidayresort.booking.notification.config.NotificationProperties;
import com.holidayresort.booking.notification.domain.ReminderDraft;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderSchedule;
import com.holidayresort.booking.notification.domain.ReminderStatus;
import com.holidayresort.booking.notification.email.EmailDeliveryException;
import com.holidayresort.booking.notification.email.EmailMessage;
import com.holidayresort.booking.notification.email.EmailTransport;
import com.holidayresort.booking.notification.email.ReminderEmailComposer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-29T02:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate TODAY = LocalDate.now(Clock.fixed(NOW, ZONE));

    @Test
    void schedulesTheReminderOneDayBeforeCheckIn() {
        MutableClock clock = new MutableClock(NOW, ZONE);
        FakeReminderRepository repository = new FakeReminderRepository();
        CapturingEmailTransport email = new CapturingEmailTransport();
        ReminderService service = service(repository, email, clock);

        ReminderSchedule reminder = service.scheduleOneDayReminder(
                new ReminderRequest(" BK-1001 ", " guest@example.com ", TODAY.plusDays(3)));

        assertThat(reminder.bookingReference()).isEqualTo("BK-1001");
        assertThat(reminder.recipientEmail()).isEqualTo("guest@example.com");
        assertThat(reminder.remindOnDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(reminder.kind()).isEqualTo(ReminderKind.ONE_DAY);
        assertThat(reminder.status()).isEqualTo(ReminderStatus.PENDING);
    }

    @Test
    void identicalSchedulesAreIdempotentAndChangedContentIsRejected() {
        MutableClock clock = new MutableClock(NOW, ZONE);
        FakeReminderRepository repository = new FakeReminderRepository();
        ReminderService service = service(repository, new CapturingEmailTransport(), clock);
        ReminderRequest request = new ReminderRequest("BK-1002", "guest@example.com", TODAY.plusDays(3));

        ReminderSchedule first = service.scheduleOneDayReminder(request);
        ReminderSchedule duplicate = service.scheduleOneDayReminder(request);

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(repository.size()).isEqualTo(1);
        assertThatThrownBy(() -> service.scheduleOneDayReminder(
                new ReminderRequest("BK-1002", "other@example.com", TODAY.plusDays(3))))
                .isInstanceOfSatisfying(NotificationException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(NotificationException.Kind.CONFLICT);
                    assertThat(exception.field()).isEqualTo("bookingReference");
                });
    }

    @Test
    void dispatchSendsDueReminderAndRecordsTheMessage() {
        MutableClock clock = new MutableClock(NOW, ZONE);
        FakeReminderRepository repository = new FakeReminderRepository();
        CapturingEmailTransport email = new CapturingEmailTransport();
        ReminderService service = service(repository, email, clock);
        ReminderSchedule reminder = service.scheduleOneDayReminder(
                new ReminderRequest("BK-1003", "guest@example.com", TODAY.plusDays(1)));

        DispatchResult result = service.dispatchDue();

        assertThat(result).isEqualTo(new DispatchResult(1, 1, 0, 0, 0));
        assertThat(email.messages).hasSize(1);
        assertThat(email.messages.get(0).to()).isEqualTo("guest@example.com");
        assertThat(email.messages.get(0).body()).contains("BK-1003").contains(TODAY.plusDays(1).toString());
        assertThat(repository.findById(reminder.id()).orElseThrow().status()).isEqualTo(ReminderStatus.SENT);
    }

    @Test
    void providerFailureDefersThenBecomesTerminalAtTheConfiguredLimit() {
        MutableClock clock = new MutableClock(NOW, ZONE);
        FakeReminderRepository repository = new FakeReminderRepository();
        ReminderService service = service(repository, message -> {
            throw new EmailDeliveryException("provider unavailable");
        }, clock);
        ReminderSchedule reminder = service.scheduleOneDayReminder(
                new ReminderRequest("BK-1004", "guest@example.com", TODAY.plusDays(1)));

        assertThat(service.dispatchDue()).isEqualTo(new DispatchResult(1, 0, 1, 0, 0));
        ReminderSchedule retrying = repository.findById(reminder.id()).orElseThrow();
        assertThat(retrying.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(retrying.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));

        clock.advance(Duration.ofMinutes(15));
        assertThat(service.dispatchDue()).isEqualTo(new DispatchResult(1, 0, 0, 1, 0));
        ReminderSchedule failed = repository.findById(reminder.id()).orElseThrow();
        assertThat(failed.status()).isEqualTo(ReminderStatus.FAILED);
        assertThat(failed.attemptCount()).isEqualTo(2);
        assertThat(failed.lastError()).isEqualTo("provider unavailable");
    }

    @Test
    void rejectsInvalidInputsBeforePersisting() {
        ReminderService service = service(
                new FakeReminderRepository(), new CapturingEmailTransport(), new MutableClock(NOW, ZONE));

        assertInvalid(() -> service.scheduleOneDayReminder(
                new ReminderRequest("", "guest@example.com", TODAY.plusDays(2))), "bookingReference");
        assertInvalid(() -> service.scheduleOneDayReminder(
                new ReminderRequest("BK-1005", "not-an-email", TODAY.plusDays(2))), "recipientEmail");
        assertInvalid(() -> service.scheduleOneDayReminder(
                new ReminderRequest("BK-1006", "guest@example.com", TODAY)), "checkInDate");
    }

    private static void assertInvalid(Runnable action, String field) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(NotificationException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(NotificationException.Kind.INVALID_REQUEST);
                    assertThat(exception.field()).isEqualTo(field);
                });
    }

    private static ReminderService service(
            ReminderRepository repository, EmailTransport transport, Clock clock) {
        return new ReminderService(
                repository,
                transport,
                new ReminderEmailComposer(),
                clock,
                new NotificationProperties(false, 50, 2, Duration.ofMinutes(15), 60000));
    }

    private static final class CapturingEmailTransport implements EmailTransport {
        private final List<EmailMessage> messages = new ArrayList<>();

        @Override
        public void send(EmailMessage message) {
            messages.add(message);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class FakeReminderRepository implements ReminderRepository {
        private final Map<Long, ReminderSchedule> schedules = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public ReminderSchedule insertIfAbsent(ReminderDraft draft) {
            return findByBookingReferenceAndKind(draft.bookingReference(), draft.kind())
                    .orElseGet(() -> {
                        long id = nextId++;
                        ReminderSchedule schedule = new ReminderSchedule(
                                id,
                                draft.bookingReference(),
                                draft.recipientEmail(),
                                draft.checkInDate(),
                                draft.remindOnDate(),
                                draft.kind(),
                                ReminderStatus.PENDING,
                                0,
                                null,
                                null,
                                null,
                                null,
                                draft.createdAt(),
                                draft.createdAt(),
                                0);
                        schedules.put(id, schedule);
                        return schedule;
                    });
        }

        @Override
        public Optional<ReminderSchedule> findById(long id) {
            return Optional.ofNullable(schedules.get(id));
        }

        @Override
        public Optional<ReminderSchedule> findByBookingReferenceAndKind(
                String bookingReference, ReminderKind kind) {
            return schedules.values().stream()
                    .filter(schedule -> schedule.bookingReference().equals(bookingReference))
                    .filter(schedule -> schedule.kind() == kind)
                    .findFirst();
        }

        @Override
        public List<ReminderSchedule> findDue(Instant now, LocalDate throughDate, int limit) {
            return schedules.values().stream()
                    .filter(schedule -> schedule.status() == ReminderStatus.PENDING)
                    .filter(schedule -> !schedule.remindOnDate().isAfter(throughDate))
                    .filter(schedule -> schedule.nextAttemptAt() == null
                            || !schedule.nextAttemptAt().isAfter(now))
                    .sorted(Comparator.comparing(ReminderSchedule::id))
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean claimForSending(long id, long expectedVersion, Instant now) {
            ReminderSchedule current = schedules.get(id);
            if (current == null
                    || current.status() != ReminderStatus.PENDING
                    || current.version() != expectedVersion) {
                return false;
            }
            schedules.put(id, copy(
                    current, ReminderStatus.SENDING, current.attemptCount() + 1,
                    current.nextAttemptAt(), now, current.sentAt(), current.lastError(),
                    expectedVersion + 1));
            return true;
        }

        @Override
        public boolean markSent(long id, long expectedVersion, Instant sentAt) {
            ReminderSchedule current = schedules.get(id);
            if (current == null
                    || current.status() != ReminderStatus.SENDING
                    || current.version() != expectedVersion) {
                return false;
            }
            schedules.put(id, copy(
                    current, ReminderStatus.SENT, current.attemptCount(), null,
                    sentAt, sentAt, null, expectedVersion + 1));
            return true;
        }

        @Override
        public boolean recordFailure(
                long id,
                long expectedVersion,
                String error,
                Instant nextAttemptAt,
                Instant updatedAt) {
            ReminderSchedule current = schedules.get(id);
            if (current == null
                    || current.status() != ReminderStatus.SENDING
                    || current.version() != expectedVersion) {
                return false;
            }
            ReminderStatus status = nextAttemptAt == null ? ReminderStatus.FAILED : ReminderStatus.PENDING;
            schedules.put(id, copy(
                    current, status, current.attemptCount(), nextAttemptAt,
                    updatedAt, current.sentAt(), error, expectedVersion + 1));
            return true;
        }

        private int size() {
            return schedules.size();
        }

        private static ReminderSchedule copy(
                ReminderSchedule current,
                ReminderStatus status,
                int attemptCount,
                Instant nextAttemptAt,
                Instant updatedAt,
                Instant sentAt,
                String lastError,
                long version) {
            return new ReminderSchedule(
                    current.id(),
                    current.bookingReference(),
                    current.recipientEmail(),
                    current.checkInDate(),
                    current.remindOnDate(),
                    current.kind(),
                    status,
                    attemptCount,
                    nextAttemptAt,
                    current.lastAttemptAt(),
                    sentAt,
                    lastError,
                    current.createdAt(),
                    updatedAt,
                    version);
        }
    }
}
