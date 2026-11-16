package com.holidayresort.booking.payment.application;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.payment.application.PaymentException.Kind;
import com.holidayresort.booking.payment.application.port.PaymentProvider;
import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** D/C boundary: all payment and booking state changes happen in one DB transaction. */
@Service
public class PaymentTransactionService {

    private final PaymentRepository payments;
    private final BookingService bookings;
    private final Clock clock;

    public PaymentTransactionService(PaymentRepository payments, BookingService bookings, Clock clock) {
        this.payments = payments;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Transactional
    public Payment createPending(String paymentId, String bookingReference, String idempotencyKey,
            String providerReference) {
        Booking booking = bookings.findByReference(bookingReference);
        return payments.save(new Payment(paymentId, bookingReference, idempotencyKey,
                booking.price().totalMinor(), PaymentStatus.PENDING, providerReference, null,
                LocalDateTime.now(clock), null));
    }

    @Transactional
    public Payment applyResult(String providerReference, PaymentProvider.Result result) {
        Payment payment = find(providerReference);
        if (payment.status().isTerminal()) {
            return payment;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return switch (result.outcome()) {
            case SUCCESS -> {
                Payment updated = payments.save(payment.withStatus(
                        PaymentStatus.PAID, providerReference, null, now));
                bookings.markPaid(payment.bookingReference());
                yield updated;
            }
            case FAILED -> {
                Payment updated = payments.save(payment.withStatus(
                        PaymentStatus.FAILED, providerReference, result.message(), now));
                bookings.markFailed(payment.bookingReference());
                yield updated;
            }
            case UNKNOWN -> {
                Payment updated = payments.save(payment.withStatus(
                        PaymentStatus.UNDER_REVIEW, providerReference, result.message(), now));
                bookings.markUnderReview(payment.bookingReference());
                yield updated;
            }
            case PENDING -> payment;
        };
    }

    @Transactional
    public Payment confirmUnderReview(String providerReference) {
        Payment payment = find(providerReference);
        if (payment.status() == PaymentStatus.PAID) {
            return payment;
        }
        requireUnderReview(payment);
        Payment updated = payments.save(payment.withStatus(
                PaymentStatus.PAID, providerReference, null, LocalDateTime.now(clock)));
        bookings.markPaid(payment.bookingReference());
        return updated;
    }

    @Transactional
    public Payment rejectUnderReview(String providerReference, String reason) {
        Payment payment = find(providerReference);
        if (payment.status() == PaymentStatus.FAILED) {
            return payment;
        }
        requireUnderReview(payment);
        Payment updated = payments.save(payment.withStatus(
                PaymentStatus.FAILED, providerReference, reason, LocalDateTime.now(clock)));
        bookings.markFailed(payment.bookingReference());
        return updated;
    }

    private Payment find(String providerReference) {
        return payments.findAll().stream()
                .filter(p -> providerReference.equals(p.providerReference()))
                .findFirst()
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "providerRef",
                        "Payment record not found."));
    }

    private static void requireUnderReview(Payment payment) {
        if (payment.status() != PaymentStatus.UNDER_REVIEW) {
            throw new PaymentException(Kind.CONFLICT, "payment",
                    "Only an under-review payment can be reconciled by staff.");
        }
    }
}
