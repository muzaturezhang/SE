package com.holidayresort.booking.member.application;

/** The published membership arrangement: fee, accommodation discount and validity. */
public record MembershipPlan(long feeMinor, int discountPercent, int durationDays) {
}
