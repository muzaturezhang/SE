package com.holidayresort.booking.member.web;

public class MemberUiException extends RuntimeException {

    private final int status;

    public MemberUiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
