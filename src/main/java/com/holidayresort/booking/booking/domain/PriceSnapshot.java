package com.holidayresort.booking.booking.domain;

/**
 * Frozen price at booking creation. Never recomputed afterwards, even if the
 * room type base rate changes. Amounts are CNY integer minor units (fen).
 *
 * totalMinor = nightlyRateMinor * nights * roomCount, then rounded once after
 * applying the member discount.
 */
public record PriceSnapshot(
        long nightlyRateMinor,
        long nights,
        int roomCount,
        int discountPercent,
        long subtotalMinor,
        long totalMinor) {

    public PriceSnapshot {
        if (nightlyRateMinor <= 0) {
            throw new IllegalArgumentException("Nightly rate must be positive");
        }
        if (nights <= 0) {
            throw new IllegalArgumentException("Stay must cover at least one night");
        }
        if (roomCount <= 0) {
            throw new IllegalArgumentException("Room count must be positive");
        }
        if (discountPercent < 0 || discountPercent > 99) {
            throw new IllegalArgumentException("Discount percent must be between 0 and 99");
        }
        if (subtotalMinor < 0 || totalMinor < 0) {
            throw new IllegalArgumentException("Amounts cannot be negative");
        }
    }

    /** Build a snapshot from nightly rate, stay length, room count and discount. */
    public static PriceSnapshot of(long nightlyRateMinor, long nights, int roomCount, int discountPercent) {
        long subtotal = nightlyRateMinor * nights * roomCount;
        long discountAmount = subtotal * discountPercent / 100;
        long total = subtotal - discountAmount;
        return new PriceSnapshot(nightlyRateMinor, nights, roomCount, discountPercent, subtotal, total);
    }
}
