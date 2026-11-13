package com.holidayresort.booking.notification.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.holidayresort.booking.notification.config.NotificationProperties;
import com.holidayresort.booking.notification.email.EmailTransport;
import com.holidayresort.booking.notification.email.ReminderEmailComposer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReminderDispatchLeaseTest {

    @Test
    void dispatchReclaimsStaleSendingBeforeReadingDueRows() {
        ReminderRepository repository = mock(ReminderRepository.class);
        when(repository.findDue(any(), any(), anyInt())).thenReturn(List.of());
        Clock clock = Clock.fixed(Instant.parse("2026-11-13T02:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        ReminderService service = new ReminderService(repository, mock(EmailTransport.class),
                new ReminderEmailComposer(), clock,
                new NotificationProperties(false, 50, 3, Duration.ofMinutes(15),
                        Duration.ofMinutes(5), 60_000, 9, "no-reply@example.invalid"));

        service.dispatchDue();

        verify(repository).recoverStaleSending(any(), any(), eq(3));
    }
}
