package com.holidayresort.booking.notification.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        @DefaultValue("false") boolean dispatchEnabled,
        @DefaultValue("50") @Min(1) @Max(500) int batchSize,
        @DefaultValue("3") @Min(1) @Max(10) int maxAttempts,
        @DefaultValue("15m") @NotNull Duration retryDelay,
        @DefaultValue("5m") @NotNull Duration sendLease,
        @DefaultValue("60000") long dispatchIntervalMs,
        @DefaultValue("9") @Min(0) @Max(23) int sendHour,
        @DefaultValue("no-reply@example.invalid") @NotBlank @Email String fromAddress) {

}
