package com.holidayresort.booking.notification.email;

/** Provider port; live providers must not leak their SDK types into the domain. */
public interface EmailTransport {

    void send(EmailMessage message);
}
