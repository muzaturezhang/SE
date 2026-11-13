package com.holidayresort.booking.notification.email;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Safe default adapter. It records messages and never contacts a real mailbox. */
@Component
@Profile("!smtp-email & !live-email")
public class MockEmailTransport implements EmailTransport {

    private final List<EmailMessage> sentMessages = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        sentMessages.add(message);
    }

    public List<EmailMessage> sentMessages() {
        return List.copyOf(sentMessages);
    }

    public void clear() {
        sentMessages.clear();
    }
}