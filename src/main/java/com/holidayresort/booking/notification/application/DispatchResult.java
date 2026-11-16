package com.holidayresort.booking.notification.application;

/** Summary of one due-reminder dispatch pass. */
public record DispatchResult(
        int candidates,
        int sent,
        int retrying,
        int failed,
        int skipped) {
}
