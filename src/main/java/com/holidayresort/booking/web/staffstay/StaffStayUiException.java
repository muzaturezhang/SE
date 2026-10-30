package com.holidayresort.booking.web.staffstay;

public class StaffStayUiException extends RuntimeException {
    private final int status;
    public StaffStayUiException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
