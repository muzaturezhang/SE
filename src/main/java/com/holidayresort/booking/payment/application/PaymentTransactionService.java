package com.holidayresort.booking.payment.application;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.payment.application.PaymentException.Kind;
import com.holidayresort.booking.payment.application.port.PaymentProvider;
import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentPurpose;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** D/C boundary: payment and the relevant booking/membership state update together. */
@Service
public class PaymentTransactionService {

    private final PaymentRepository payments;
    private final BookingService bookings;
    private final MembershipService memberships;
    private final Clock clock;

    /** Compatibility constructor for existing booking-only unit tests. */
    public PaymentTransactionService(PaymentRepository payments, BookingService bookings, Clock clock) {
        this(payments, bookings, clock, null);
    }

    @Autowired
    public PaymentTransactionService(PaymentRepository payments, BookingService bookings,
            Clock clock, MembershipService memberships) {
        this.payments = payments;
        this.bookings = bookings;
        this.clock = clock;
        this.memberships = memberships;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payment createPending(String paymentId, String bookingReference, String idempotencyKey,
            String providerReference) {
        Booking booking = bookings.findByReference(bookingReference);
        return payments.save(new Payment(paymentId, bookingReference, idempotencyKey,
                booking.price().totalMinor(), PaymentStatus.PENDING, providerReference, null,
                LocalDateTime.now(clock), null, PaymentPurpose.BOOKING, booking.customerId()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payment createMembershipPending(String paymentId, String customerId, long amountMinor,
            String idempotencyKey, String providerReference) {
        return payments.save(new Payment(paymentId, null, idempotencyKey, amountMinor,
                PaymentStatus.PENDING, providerReference, null, LocalDateTime.now(clock), null,
                PaymentPurpose.MEMBERSHIP, customerId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
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
                onSuccess(updated, now);
                yield updated;
            }
            case FAILED -> {
                Payment updated = payments.save(payment.withStatus(
                        PaymentStatus.FAILED, providerReference, result.message(), now));
                onFailure(updated);
                yield updated;
            }
            case UNKNOWN -> {
                Payment updated = payments.save(payment.withStatus(
                        PaymentStatus.UNDER_REVIEW, providerReference, result.message(), now));
                onUnknown(updated);
                yield updated;
            }
            case PENDING -> payment;
        };
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payment confirmUnderReview(String providerReference) {
        Payment payment = find(providerReference);
        if (payment.status() == PaymentStatus.PAID) {
            return payment;
        }
        requireUnderReview(payment);
        LocalDateTime now = LocalDateTime.now(clock);
        Payment updated = payments.save(payment.withStatus(
                PaymentStatus.PAID, providerReference, null, now));
        onSuccess(updated, now);
        return updated;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Payment rejectUnderReview(String providerReference, String reason) {
        Payment payment = find(providerReference);
        if (payment.status() == PaymentStatus.FAILED) {
            return payment;
        }
        requireUnderReview(payment);
        Payment updated = payments.save(payment.withStatus(
                PaymentStatus.FAILED, providerReference, reason, LocalDateTime.now(clock)));
        onFailure(updated);
        return updated;
    }

    private void onSuccess(Payment payment, LocalDateTime at) {
        if (payment.purpose() == PaymentPurpose.MEMBERSHIP) {
            if (memberships == null) {
                throw new PaymentException(Kind.CONFLICT, "membership",
                        "Membership service is not configured.");
            }
            memberships.confirmPayment(payment.customerId(), at.toLocalDate());
        } else {
            bookings.markPaid(payment.bookingReference());
        }
    }

    private void onFailure(Payment payment) {
        if (payment.purpose() == PaymentPurpose.BOOKING) {
            bookings.markFailed(payment.bookingReference());
        }
    }

    private void onUnknown(Payment payment) {
        if (payment.purpose() == PaymentPurpose.BOOKING) {
            bookings.markUnderReview(payment.bookingReference());
        }
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
