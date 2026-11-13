package com.holidayresort.booking.notification.application;

import com.holidayresort.booking.notification.domain.ReminderSchedule;

/** D-owned application port consumed by Booking and the scheduler. */
public interface NotificationService {

    /**
     * Creates or replaces the one current reminder revision for a booking.
     *
     * <p>Identical calls are idempotent. A changed check-in, check-out, recipient or room type
     * replaces the pending delivery automatically and records a reminder audit event.
     */
    ReminderSchedule scheduleOneDayReminder(ReminderRequest request);

    /** Cancels future delivery; repeated calls are harmless. */
    boolean cancelOneDayReminder(String bookingReference);

    /** Sends all currently due reminders in one bounded batch. */
    DispatchResult dispatchDue();
}