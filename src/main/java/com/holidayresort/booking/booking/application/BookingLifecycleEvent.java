package com.holidayresort.booking.booking.application;

import com.holidayresort.booking.booking.domain.Booking;

/** In-process booking lifecycle event consumed by D's reminder adapter. */
public record BookingLifecycleEvent(Type type, Booking booking, String roomTypeName) {

    public enum Type {
        CREATED,
        STATUS_CHANGED,
        CHANGED
    }
}
