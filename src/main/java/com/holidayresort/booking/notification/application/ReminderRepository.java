package com.holidayresort.booking.notification.application;

import com.holidayresort.booking.notification.domain.ReminderDraft;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** D-owned persistence port for reminder scheduling and dispatch state. */
public interface ReminderRepository {

    /** Inserts a revision or atomically replaces a changed current revision. */
    ReminderSchedule upsert(ReminderDraft draft);

    Optional<ReminderSchedule> findById(long id);

    Optional<ReminderSchedule> findByBookingReferenceAndKind(String bookingReference, ReminderKind kind);

    List<ReminderSchedule> findDue(Instant now, LocalDate throughDate, int limit);

    /** Cancels future delivery and returns whether a schedule existed. */
    boolean cancelByBookingReference(String bookingReference, Instant updatedAt);

    boolean claimForSending(long id, long expectedVersion, Instant now);

    boolean markSent(long id, long expectedVersion, Instant sentAt);

    boolean recordFailure(
            long id,
            long expectedVersion,
            String errorCode,
            Instant nextAttemptAt,
            Instant updatedAt);
    /** Reclaims rows abandoned in SENDING after a process crash. */
    default int recoverStaleSending(Instant cutoff, Instant now, int maxAttempts) {
        return 0;
    }
}
