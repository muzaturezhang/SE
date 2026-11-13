package com.holidayresort.booking.notification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.holidayresort.booking.notification.config.NotificationProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailTransportTest {

    private static final NotificationProperties PROPERTIES =
            new NotificationProperties(false, 50, 3, Duration.ofMinutes(15), Duration.ofMinutes(5), 60_000, 9,
                    "booking@example.test");

    @Test
    void delegatesAProviderNeutralMessageToSpringMail() {
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpEmailTransport transport = new SmtpEmailTransport(sender, PROPERTIES);

        transport.send(new EmailMessage("guest@example.com", "入住提醒", "预订编号：BK-1"));
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());

        assertThat(captor.getValue().getFrom()).isEqualTo("booking@example.test");
        assertThat(captor.getValue().getTo()).containsExactly("guest@example.com");
        assertThat(captor.getValue().getSubject()).isEqualTo("入住提醒");
        assertThat(captor.getValue().getText()).contains("BK-1");
    }

    @Test
    void convertsProviderFailureToAPrivacySafeDeliveryCode() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("recipient guest@example.com failed"))
                .when(sender).send(any(SimpleMailMessage.class));
        SmtpEmailTransport transport = new SmtpEmailTransport(sender, PROPERTIES);

        assertThatThrownBy(() -> transport.send(
                new EmailMessage("guest@example.com", "入住提醒", "body")))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception ->
                        assertThat(exception.deliveryCode()).isEqualTo("SMTP_SEND_FAILED"));
    }
}
