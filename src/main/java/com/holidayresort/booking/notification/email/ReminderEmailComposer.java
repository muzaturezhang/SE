package com.holidayresort.booking.notification.email;

import com.holidayresort.booking.notification.domain.ReminderSchedule;
import org.springframework.stereotype.Component;

/** Builds a stable plain-text reminder without account credentials. */
@Component
public class ReminderEmailComposer {

    public EmailMessage compose(ReminderSchedule reminder) {
        String subject = "入住提醒：%s".formatted(reminder.checkInDate());
        String body = """
                您好：

                这是您的入住提醒。
                预订编号：%s
                入住日期：%s
                退房日期：%s
                房型：%s

                请按预订确认信息安排行程。
                """.formatted(
                reminder.bookingReference(),
                reminder.checkInDate(),
                reminder.checkOutDate(),
                reminder.roomTypeName());
        return new EmailMessage(reminder.recipientEmail(), subject, body.strip());
    }
}