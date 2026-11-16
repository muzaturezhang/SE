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
    private static final NotificationProperties PROPERTIES =
            new NotificationProperties(false, 50, 3, Duration.ofMinutes(15), Duration.ofMinutes(5), 60_000, 9,
                    "no-reply@example.invalid");

    @Test
    void schedulesAtNineOnTheDayBeforeCheckInInShanghai() {
        MutableClock clock = new MutableClock(NOW, ZONE);
        FakeReminderRepository repository = new FakeReminderRepository();
        ReminderService service = service(repository, new CapturingEmailTransport(), clock);

        ReminderSchedule reminder = service.scheduleOneDayReminder(
                request(" BK-1001 ", " guest@example.com ", TODAY.plusDays(3), "露台套房"));

        assertThat(reminder.bookingReference()).isEqualTo("BK-1001");
        assertThat(reminder.recipientEmail()).isEqualTo("guest@example.com");
        assertThat(reminder.checkOutDate()).isEqualTo(TODAY.plusDays(5));
        assertThat(reminder.roomTypeName()).isEqualTo("露台套房");
        assertThat(reminder.remindOnDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(reminder.scheduledAt()).isEqualTo(
                TODAY.plusDays(2).atTime(9, 0).atZone(ZONE).toInstant());
        assertThat(reminder.status()).isEqualTo(ReminderStatus.PENDING);
    }

    @Test
    void identicalSchedulesAreIdempotentAndChangedBookingsAreRescheduled() {
        FakeReminderRepository repository = new FakeReminderRepository();
        ReminderService service = service(repository, new CapturingEmailTransport(),
                new MutableClock(NOW, ZONE));
        ReminderRequest original = request(
                "BK-1002", "guest@example.com", TODAY.plusDays(3), "庭院客房");

        ReminderSchedule first = service.scheduleOneDayReminder(original);
        ReminderSchedule duplicate = service.scheduleOneDayReminder(original);
        ReminderSchedule changed = service.scheduleOneDayReminder(
                request("BK-1002", "other@example.com", TODAY.plusDays(5), "泳池别墅"));

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(changed.id()).isEqualTo(first.id());
        assertThat(changed.recipientEmail()).isEqualTo("other@example.com");
        assertThat(changed.checkInDate()).isEqualTo(TODAY.plusDays(5));
        assertThat(changed.roomTypeName()).isEqualTo("泳池别墅");
        assertThat(changed.version()).isGreaterThan(first.version());
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void dispatchSendsTheRequiredBookingSnapshotAndMarksSent() {
        FakeReminderRepository repository = new FakeReminderRepository();
        CapturingEmailTransport email = new CapturingEmailTransport();
        ReminderService service = service(repository, email, new MutableClock(NOW, ZONE));
        ReminderSchedule reminder = service.scheduleOneDayReminder(
                request("BK-1003", "guest@example.com", TODAY.plusDays(1), "庭院客房"));

        DispatchResult result = service.dispatchDue();

        assertThat(result).isEqualTo(new DispatchResult(1, 1, 0, 0, 0));
        assertThat(email.messages).hasSize(1);
        assertThat(email.messages.get(0).to()).isEqualTo("guest@example.com");
        assertThat(email.messages.get(0).body())
                .contains("BK-1003", TODAY.plusDays(1).toString(),
                        TODAY.plusDays(3).toString(), "庭院客房")
                .doesNotContain("password");
        assertThat(repository.findById(reminder.id()).orElseThrow().status())
                .isEqualTo(ReminderStatus.SENT);
    }

    @Test
    void providerFailureRetriesThenFailsWithAPrivacySafeErrorCode() {
        MutableClock clock = new MutableClock(NOW, ZONE);
        FakeReminderRepository repository = new FakeReminderRepository();
        ReminderService service = service(repository, message -> {
            throw new EmailDeliveryException(
                    "SMTP_SEND_FAILED", "recipient guest@example.com rejected");
        }, clock);
        ReminderSchedule reminder = service.scheduleOneDayReminder(
                request("BK-1004", "guest@example.com", TODAY.plusDays(1), "泳池别墅"));

        DispatchResult first = service.dispatchDue();
        assertThat(first).isEqualTo(new DispatchResult(1, 0, 1, 0, 0));
        ReminderSchedule retrying = repository.findById(reminder.id()).orElseThrow();
        assertThat(retrying.status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(retrying.lastError()).isEqualTo("SMTP_SEND_FAILED");
        assertThat(retrying.lastError()).doesNotContain("guest@example.com");

        clock.advance(Duration.ofMinutes(15));
        assertThat(service.dispatchDue()).isEqualTo(new DispatchResult(1, 0, 1, 0, 0));
        clock.advance(Duration.ofMinutes(15));
        assertThat(service.dispatchDue()).isEqualTo(new DispatchResult(1, 0, 0, 1, 0));
        assertThat(repository.findById(reminder.id()).orElseThrow().status())
                .isEqualTo(ReminderStatus.FAILED);
    }

    @Test
    void cancellationPreventsDeliveryAndIsIdempotent() {
        FakeReminderRepository repository = new FakeReminderRepository();
        CapturingEmailTransport email = new CapturingEmailTransport();
        ReminderService service = service(repository, email, new MutableClock(NOW, ZONE));
        ReminderSchedule reminder = service.scheduleOneDayReminder(
                request("BK-1005", "guest@example.com", TODAY.plusDays(1), "庭院客房"));

        assertThat(service.cancelOneDayReminder(" BK-1005 ")).isTrue();
        assertThat(service.cancelOneDayReminder("BK-1005")).isTrue();
        assertThat(service.dispatchDue()).isEqualTo(new DispatchResult(0, 0, 0, 0, 0));
        assertThat(email.messages).isEmpty();
        assertThat(repository.findById(reminder.id()).orElseThrow().status())
                .isEqualTo(ReminderStatus.CANCELLED);
    }

    @Test
    void invalidStayAndDisplayDataAreRejectedBeforePersistence() {
        FakeReminderRepository repository = new FakeReminderRepository();
        ReminderService service = service(repository, new CapturingEmailTransport(),
                new MutableClock(NOW, ZONE));

        assertThatThrownBy(() -> service.scheduleOneDayReminder(
                request("BK-1006", "bad-email", TODAY.plusDays(1), "庭院客房")))
                .isInstanceOfSatisfying(NotificationException.class, exception ->
                        assertThat(exception.field()).isEqualTo("recipientEmail"));
        assertThatThrownBy(() -> service.scheduleOneDayReminder(
                request("BK-1007", "guest@example.com", TODAY, "庭院客房")))
                .isInstanceOfSatisfying(NotificationException.class, exception ->
                        assertThat(exception.field()).isEqualTo("checkInDate"));
        assertThatThrownBy(() -> service.scheduleOneDayReminder(new ReminderRequest(
                "BK-1008", "guest@example.com", TODAY.plusDays(2),
                TODAY.plusDays(2), "庭院客房")))
                .isInstanceOfSatisfying(NotificationException.class, exception ->
                        assertThat(exception.field()).isEqualTo("checkOutDate"));
        assertThatThrownBy(() -> service.scheduleOneDayReminder(
                request("BK-1009", "guest@example.com", TODAY.plusDays(1), " ")))
                .isInstanceOfSatisfying(NotificationException.class, exception ->
                        assertThat(exception.field()).isEqualTo("roomTypeName"));
        assertThat(repository.size()).isZero();
    }

    private static ReminderService service(
            FakeReminderRepository repository,
            EmailTransport email,
            MutableClock clock) {
        return new ReminderService(repository, email, new ReminderEmailComposer(), clock, PROPERTIES);
    }

    private static ReminderRequest request(
            String reference, String email, LocalDate checkIn, String roomType) {
        return new ReminderRequest(reference, email, checkIn, checkIn.plusDays(2), roomType);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
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

    private static final class CapturingEmailTransport implements EmailTransport {
        private final List<EmailMessage> messages = new ArrayList<>();

        @Override
        public void send(EmailMessage message) {
            messages.add(message);
        }
    }

    private static final class FakeReminderRepository implements ReminderRepository {
        private final Map<Long, ReminderSchedule> rows = new LinkedHashMap<>();
        private long nextId = 1;

        @Override
        public ReminderSchedule upsert(ReminderDraft draft) {
            Optional<ReminderSchedule> existing = findByBookingReferenceAndKind(
                    draft.bookingReference(), draft.kind());
            if (existing.isEmpty()) {
                ReminderSchedule inserted = new ReminderSchedule(
                        nextId++, draft.bookingReference(), draft.recipientEmail(),
                        draft.checkInDate(), draft.checkOutDate(), draft.roomTypeName(),
                        draft.remindOnDate(), draft.scheduledAt(), draft.kind(),
                        ReminderStatus.PENDING, 0, null, null, null, null,
                        draft.createdAt(), draft.createdAt(), 0);
                rows.put(inserted.id(), inserted);
                return inserted;
            }
            ReminderSchedule previous = existing.orElseThrow();
            boolean same = previous.recipientEmail().equals(draft.recipientEmail())
                    && previous.checkInDate().equals(draft.checkInDate())
                    && previous.checkOutDate().equals(draft.checkOutDate())
                    && previous.roomTypeName().equals(draft.roomTypeName())
                    && previous.remindOnDate().equals(draft.remindOnDate())
                    && previous.scheduledAt().equals(draft.scheduledAt())
                    && previous.status() != ReminderStatus.CANCELLED;
            if (same) {
                return previous;
            }
            ReminderSchedule changed = new ReminderSchedule(
                    previous.id(), draft.bookingReference(), draft.recipientEmail(),
                    draft.checkInDate(), draft.checkOutDate(), draft.roomTypeName(),
                    draft.remindOnDate(), draft.scheduledAt(), draft.kind(),
                    ReminderStatus.PENDING, 0, null, null, null, null,
                    previous.createdAt(), draft.createdAt(), previous.version() + 1);
            rows.put(changed.id(), changed);
            return changed;
        }

        @Override
        public Optional<ReminderSchedule> findById(long id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<ReminderSchedule> findByBookingReferenceAndKind(
                String bookingReference, ReminderKind kind) {
            return rows.values().stream()
                    .filter(row -> row.bookingReference().equals(bookingReference)
                            && row.kind() == kind)
                    .findFirst();
        }

        @Override
        public List<ReminderSchedule> findDue(Instant now, LocalDate throughDate, int limit) {
            return rows.values().stream()
                    .filter(row -> row.status() == ReminderStatus.PENDING)
                    .filter(row -> !row.scheduledAt().isAfter(now))
                    .filter(row -> row.nextAttemptAt() == null || !row.nextAttemptAt().isAfter(now))
                    .sorted(Comparator.comparing(ReminderSchedule::id))
                    .limit(limit)
                    .toList();
        }

        @Override
        public boolean cancelByBookingReference(String bookingReference, Instant updatedAt) {
            Optional<ReminderSchedule> existing =
                    findByBookingReferenceAndKind(bookingReference, ReminderKind.ONE_DAY);
            if (existing.isEmpty()) {
                return false;
            }
            ReminderSchedule old = existing.orElseThrow();
            if (old.status() == ReminderStatus.CANCELLED) {
                return true;
            }
            rows.put(old.id(), new ReminderSchedule(
                    old.id(), old.bookingReference(), old.recipientEmail(), old.checkInDate(),
                    old.checkOutDate(), old.roomTypeName(), old.remindOnDate(), old.scheduledAt(),
                    old.kind(), ReminderStatus.CANCELLED, old.attemptCount(), null,
                    old.lastAttemptAt(), old.sentAt(), old.lastError(), old.createdAt(),
                    updatedAt, old.version() + 1));
            return true;
        }

        @Override
        public boolean claimForSending(long id, long expectedVersion, Instant now) {
            ReminderSchedule old = rows.get(id);
            if (old == null || old.version() != expectedVersion || old.status() != ReminderStatus.PENDING) {
                return false;
            }
            rows.put(id, copy(old, ReminderStatus.SENDING, old.attemptCount() + 1,
                    old.nextAttemptAt(), old.lastAttemptAt(), old.sentAt(), old.lastError(),
                    old.updatedAt().isAfter(now) ? old.updatedAt() : now, old.version() + 1));
            return true;
        }

        @Override
        public boolean markSent(long id, long expectedVersion, Instant sentAt) {
            ReminderSchedule old = rows.get(id);
            if (old == null || old.version() != expectedVersion || old.status() != ReminderStatus.SENDING) {
                return false;
            }
            rows.put(id, copy(old, ReminderStatus.SENT, old.attemptCount(), null,
                    old.lastAttemptAt(), sentAt, null, sentAt, old.version() + 1));
            return true;
        }

        @Override
        public boolean recordFailure(
                long id, long expectedVersion, String errorCode,
                Instant nextAttemptAt, Instant updatedAt) {
            ReminderSchedule old = rows.get(id);
            if (old == null || old.version() != expectedVersion || old.status() != ReminderStatus.SENDING) {
                return false;
            }
            ReminderStatus status = nextAttemptAt == null
                    ? ReminderStatus.FAILED : ReminderStatus.PENDING;
            rows.put(id, copy(old, status, old.attemptCount(), nextAttemptAt,
                    old.lastAttemptAt(), old.sentAt(), errorCode, updatedAt, old.version() + 1));
            return true;
        }

        int size() {
            return rows.size();
        }

        private static ReminderSchedule copy(
                ReminderSchedule old, ReminderStatus status, int attemptCount,
                Instant nextAttemptAt, Instant lastAttemptAt, Instant sentAt,
                String lastError, Instant updatedAt, long version) {
            return new ReminderSchedule(
                    old.id(), old.bookingReference(), old.recipientEmail(), old.checkInDate(),
                    old.checkOutDate(), old.roomTypeName(), old.remindOnDate(), old.scheduledAt(),
                    old.kind(), status, attemptCount, nextAttemptAt, lastAttemptAt, sentAt,
                    lastError, old.createdAt(), updatedAt, version);
        }
    }
}
