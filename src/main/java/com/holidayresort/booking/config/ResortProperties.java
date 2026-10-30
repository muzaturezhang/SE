package com.holidayresort.booking.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record ResortProperties(
        @NotNull ZoneId timezone,
        @Pattern(regexp = "CNY") String currency,
        @Min(1) int bookingHoldMinutes) {
}
