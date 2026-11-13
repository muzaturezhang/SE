package com.holidayresort.booking.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** A-owned membership standard: fee in CNY minor units, discount and validity. */
@Validated
@ConfigurationProperties(prefix = "app.membership")
public record MembershipProperties(
        @Min(1) long feeMinor,
        @Min(1) @Max(99) int discountPercent,
        @Min(1) int durationDays) {
}
