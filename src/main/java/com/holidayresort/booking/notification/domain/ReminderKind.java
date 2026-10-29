package com.holidayresort.booking.notification.domain;

/** Reminder kinds that the notification module currently owns. */
public enum ReminderKind {
    ONE_DAY(1);

    private final int daysBefore;

    ReminderKind(int daysBefore) {
        this.daysBefore = daysBefore;
    }

    public int daysBefore() {
        return daysBefore;
    }
}
