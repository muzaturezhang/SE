package com.holidayresort.booking.member.application;

public class MemberAccountException extends RuntimeException {

    public enum Kind { INVALID, CONFLICT, NOT_FOUND }

    private final Kind kind;
    private final String field;

    public MemberAccountException(Kind kind, String field, String message) {
        super(message);
        this.kind = kind;
        this.field = field;
    }

    public Kind kind() {
        return kind;
    }

    public String field() {
        return field;
    }
}
