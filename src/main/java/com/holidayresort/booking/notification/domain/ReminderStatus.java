package com.holidayresort.booking.notification.domain;

/** Persistence states used by the reminder dispatcher. */
public enum ReminderStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED
}