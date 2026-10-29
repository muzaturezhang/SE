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

    ReminderSchedule insertIfAbsent(ReminderDraft draft);

    Optional<ReminderSchedule> findById(long id);

    Optional<ReminderSchedule> findByBookingReferenceAndKind(String bookingReference, ReminderKind kind);

    List<ReminderSchedule> findDue(Instant now, LocalDate throughDate, int limit);

    boolean claimForSending(long id, long expectedVersion, Instant now);

    boolean markSent(long id, long expectedVersion, Instant sentAt);

    boolean recordFailure(
            long id,
            long expectedVersion,
            String error,
            Instant nextAttemptAt,
            Instant updatedAt);
}
