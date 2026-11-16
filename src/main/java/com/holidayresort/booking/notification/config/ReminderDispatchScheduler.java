package com.holidayresort.booking.notification.config;

import com.holidayresort.booking.notification.application.ReminderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs due reminder dispatch only when the team explicitly enables it. */
@Component
@ConditionalOnProperty(prefix = "notification", name = "dispatch-enabled", havingValue = "true")
public class ReminderDispatchScheduler {

    private final ReminderService reminderService;

    public ReminderDispatchScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${notification.dispatch-interval-ms:60000}")
    public void dispatchDue() {
        reminderService.dispatchDue();
    }
}
