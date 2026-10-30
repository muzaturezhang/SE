package com.holidayresort.booking.web.stay;

public class StayUiException extends RuntimeException {
    private final int status;
    public StayUiException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
