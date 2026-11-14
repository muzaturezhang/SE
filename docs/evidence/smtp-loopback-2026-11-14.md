# SMTP Loopback Delivery Evidence

## Scope

This evidence records a real SMTP protocol exchange between the project's
`SmtpEmailTransport` and a temporary SMTP test mailbox bound to the loopback
interface. It does not claim delivery through an external mailbox provider,
MailHog, Mailtrap, or a production SMTP server.

- Date/time: 2026-11-14 12:48:54 +08:00
- Java: 17.0.20.1
- SMTP endpoint: ephemeral `127.0.0.1` port owned by the test
- Test: `com.holidayresort.booking.notification.email.SmtpLoopbackDeliveryTest`
- Command: `.\scripts\maven.ps1 --batch-mode --no-transfer-progress "-Dtest=SmtpLoopbackDeliveryTest" test`

## Result

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The test asserted:

- sender `booking@example.test`
- recipient `guest@example.invalid`
- subject `入住提醒`
- body contained booking reference `BOOK-SMTP-EVIDENCE` and stay date `2027-01-10`
- the SMTP command sequence included `MAIL FROM:`, `RCPT TO:` and `DATA`
- the captured message contained no `password`, `username` or `cardNumber` fields

The concise Surefire result is retained in
`smtp-loopback-surefire-2026-11-14.txt`.

## Remaining external-service evidence

External SMTP delivery still requires an actual test mailbox or provider
credential. It must not be reported as verified until such a delivery is
performed and its safe evidence is recorded.