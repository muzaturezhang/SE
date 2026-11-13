package com.holidayresort.booking.member.application;

/**
 * Member discount projection consumed by Booking/Payment. The final amount
 * remains C's responsibility; A supplies eligibility and the percentage.
 */
public record DiscountResult(boolean active, int discountPercent, String label) {

    public static DiscountResult inactive(String label) {
        return new DiscountResult(false, 0, label);
    }
}
