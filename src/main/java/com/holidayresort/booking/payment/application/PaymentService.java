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
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * C-owned payment lifecycle: initiation, callback handling, reconciliation.
 *
 * <p>Double-charge prevention:
 * <ul>
 *   <li>Idempotency key on initiation — retried submit does not create two payments.</li>
 *   <li>PAID is reached at most once; a second success callback is a no-op.</li>
 *   <li>Unknown / timeout results move to UNDER_REVIEW and are never auto-retried.</li>
 * </ul>
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final BookingService bookings;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentProvider provider,
                         BookingService bookings, Clock clock) {
        this.payments = payments;
        this.provider = provider;
        this.bookings = bookings;
        this.clock = clock;
    }

    /**
     * Initiate payment for a booking. Idempotent: if a PENDING payment already
     * exists for this booking, return it instead of creating a new one.
     */
    public Payment initiatePayment(String bookingReference, String customerId) {
        Booking booking = bookings.findByReference(bookingReference);
        if (booking.status() == com.holidayresort.booking.booking.domain.BookingStatus.PAID) {
            throw new PaymentException(Kind.CONFLICT, "booking", "Booking is already paid.");
        }
        var existing = payments.findByBookingReference(bookingReference);
        if (existing.isPresent() && existing.get().status() == PaymentStatus.PENDING) {
            return existing.get();
        }
        PaymentProvider.Initiation initiation = provider.initiate(
                bookingReference, booking.price().totalMinor(), customerId);
        Payment payment = new Payment(
                "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                bookingReference,
                "idem-" + bookingReference,
                booking.price().totalMinor(),
                PaymentStatus.PENDING,
                initiation.providerReference(),
                null,
                LocalDateTime.now(clock),
                null);
        return payments.save(payment);
    }

    /**
     * Handle a provider callback or reconciliation query.
     */
    public Payment handleCallback(String providerReference) {
        Payment payment = payments.findAll().stream()
                .filter(p -> providerReference.equals(p.providerReference()))
                .findFirst()
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "providerRef",
                        "Payment record not found."));
        if (payment.status().isTerminal()) {
            return payment;
        }
        PaymentProvider.Result result = provider.query(providerReference);
        return switch (result.outcome()) {
            case SUCCESS -> {
                Payment updated = payments.save(payment.withStatus(PaymentStatus.PAID,
                        providerReference, null));
                bookings.markPaid(payment.bookingReference());
                yield updated;
            }
            case FAILED -> {
                Payment updated = payments.save(payment.withStatus(PaymentStatus.FAILED,
                        providerReference, result.message()));
                bookings.markFailed(payment.bookingReference());
                yield updated;
            }
            case UNKNOWN -> {
                Payment updated = payments.save(payment.withStatus(PaymentStatus.UNDER_REVIEW,
                        providerReference, result.message()));
                bookings.markUnderReview(payment.bookingReference());
                yield updated;
            }
            case PENDING -> payment;
        };
    }

    /** Staff reconciliation: force-confirm a payment that was in UNDER_REVIEW. */
    public Payment staffConfirmPaid(String providerReference) {
        Payment payment = payments.findAll().stream()
                .filter(p -> providerReference.equals(p.providerReference()))
                .findFirst()
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "providerRef",
                        "Payment record not found."));
        Payment updated = payments.save(payment.withStatus(PaymentStatus.PAID, providerReference, null));
        bookings.markPaid(payment.bookingReference());
        return updated;
    }

    /** Staff reconciliation: force-fail a payment that was in UNDER_REVIEW. */
    public Payment staffReject(String providerReference, String reason) {
        Payment payment = payments.findAll().stream()
                .filter(p -> providerReference.equals(p.providerReference()))
                .findFirst()
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "providerRef",
                        "Payment record not found."));
        Payment updated = payments.save(payment.withStatus(PaymentStatus.FAILED, providerReference, reason));
        bookings.markFailed(payment.bookingReference());
        return updated;
    }

    public Payment findByBookingReference(String bookingReference) {
        return payments.findByBookingReference(bookingReference)
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "booking",
                        "No payment record for this booking."));
    }
}
