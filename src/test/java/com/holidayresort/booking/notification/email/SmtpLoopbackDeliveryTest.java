package com.holidayresort.booking.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import com.holidayresort.booking.notification.config.NotificationProperties;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Real loopback SMTP conversation used as controlled delivery evidence; no external provider is contacted. */
class SmtpLoopbackDeliveryTest {

    private static final NotificationProperties PROPERTIES =
            new NotificationProperties(false, 50, 3, Duration.ofMinutes(15), Duration.ofMinutes(5),
                    60_000, 9, "booking@example.test");

    @Test
    void sendsThroughRealSmtpProtocolToLoopbackTestMailbox() throws Exception {
        try (SmtpSink sink = new SmtpSink()) {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(InetAddress.getLoopbackAddress().getHostAddress());
            sender.setPort(sink.port());
            sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
            Properties mailProperties = sender.getJavaMailProperties();
            mailProperties.put("mail.smtp.auth", "false");
            mailProperties.put("mail.smtp.starttls.enable", "false");
            mailProperties.put("mail.smtp.connectiontimeout", "3000");
            mailProperties.put("mail.smtp.timeout", "3000");
            mailProperties.put("mail.smtp.writetimeout", "3000");

            new SmtpEmailTransport(sender, PROPERTIES).send(new EmailMessage(
                    "guest@example.invalid", "入住提醒", "预订编号：BOOK-SMTP-EVIDENCE\n入住日期：2027-01-10"));

            String raw = sink.awaitMessage(5, TimeUnit.SECONDS);
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(raw.getBytes(StandardCharsets.ISO_8859_1)));
            assertThat(message.getFrom()).extracting(Object::toString).containsExactly("booking@example.test");
            assertThat(message.getAllRecipients()).extracting(Object::toString)
                    .containsExactly("guest@example.invalid");
            assertThat(message.getSubject()).isEqualTo("入住提醒");
            assertThat(content(message)).contains("BOOK-SMTP-EVIDENCE", "2027-01-10");
            assertThat(raw).doesNotContain("password", "username", "cardNumber");
            assertThat(sink.commands()).anyMatch(command -> command.startsWith("MAIL FROM:"))
                    .anyMatch(command -> command.startsWith("RCPT TO:"))
                    .anyMatch(command -> command.startsWith("DATA"));
        }
    }

    private static String content(MimeMessage message) throws Exception {
        Object content = message.getContent();
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                text.append(multipart.getBodyPart(index).getContent()).append('\n');
            }
            return text.toString();
        }
        return String.valueOf(content);
    }

    private static final class SmtpSink implements AutoCloseable {
        private final ServerSocket server;
        private final CompletableFuture<String> message = new CompletableFuture<>();
        private final CompletableFuture<java.util.List<String>> commands = new CompletableFuture<>();
        private final Thread thread;

        SmtpSink() throws Exception {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            thread = new Thread(this::serve, "loopback-smtp");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        String awaitMessage(long timeout, TimeUnit unit) throws Exception {
            return message.get(timeout, unit);
        }

        java.util.List<String> commands() throws Exception {
            return commands.get(5, TimeUnit.SECONDS);
        }

        private void serve() {
            java.util.List<String> receivedCommands = new java.util.ArrayList<>();
            try (Socket socket = server.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         socket.getInputStream(), StandardCharsets.ISO_8859_1));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.ISO_8859_1)) {
                reply(writer, "220 localhost ESMTP Holiday Resort test sink");
                String line;
                while ((line = reader.readLine()) != null) {
                    receivedCommands.add(line);
                    String upper = line.toUpperCase(java.util.Locale.ROOT);
                    if (upper.startsWith("EHLO")) {
                        writer.print("250-localhost\r\n250-8BITMIME\r\n250 SIZE 1000000\r\n");
                        writer.flush();
                    } else if (upper.startsWith("HELO") || upper.startsWith("MAIL FROM:")
                            || upper.startsWith("RCPT TO:") || upper.startsWith("RSET")) {
                        reply(writer, "250 OK");
                    } else if (upper.equals("DATA")) {
                        reply(writer, "354 End data with <CR><LF>.<CR><LF>");
                        StringBuilder data = new StringBuilder();
                        String dataLine;
                        while ((dataLine = reader.readLine()) != null && !dataLine.equals(".")) {
                            data.append(dataLine.startsWith("..") ? dataLine.substring(1) : dataLine).append("\r\n");
                        }
                        message.complete(data.toString());
                        reply(writer, "250 2.0.0 queued as SMTP-EVIDENCE");
                    } else if (upper.equals("QUIT")) {
                        reply(writer, "221 2.0.0 bye");
                        break;
                    } else {
                        reply(writer, "250 OK");
                    }
                }
                commands.complete(java.util.List.copyOf(receivedCommands));
            } catch (Exception exception) {
                message.completeExceptionally(exception);
                commands.completeExceptionally(exception);
            }
        }

        private static void reply(PrintWriter writer, String response) {
            writer.print(response + "\r\n");
            writer.flush();
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(2_000);
        }
    }
}