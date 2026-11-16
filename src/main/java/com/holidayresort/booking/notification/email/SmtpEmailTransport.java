package com.holidayresort.booking.notification.email;

import com.holidayresort.booking.notification.config.NotificationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Opt-in SMTP adapter. Credentials and host settings come only from environment-backed Spring
 * Mail properties; no connection details are defined in source.
 */
@Component
@Profile("smtp-email")
public class SmtpEmailTransport implements EmailTransport {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailTransport(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.fromAddress());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        try {
            mailSender.send(mail);
        } catch (MailException exception) {
            throw new EmailDeliveryException("SMTP_SEND_FAILED", "SMTP delivery failed", exception);
        }
    }
}