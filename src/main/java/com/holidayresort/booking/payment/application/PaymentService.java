package com.holidayresort.booking.payment.application;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.payment.application.port.PaymentProvider;
import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * C-owned payment orchestration: initiate payment, handle provider callbacks,
 * reconcile under-review payments, and trigger booking state transitions.
 *
 * <p>Idempotency: a callback for the same providerPaymentId is processed once.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final BookingService bookings;
    private final MembershipService memberships;
    private final Clock clock;

    public PaymentService(PaymentRepository payments,
                          PaymentProvider provider,
                          BookingService bookings,
                          MembershipService memberships,
                          Clock clock) {
        this.payments = payments;
        this.provider = provider;
        this.bookings = bookings;
        this.memberships = memberships;
        this.clock = clock;
    }

    /** Initiate a payment for a pending booking. Returns the payment record. */
    public Payment initiatePayment(String bookingReference) {
        Booking booking = bookings.findByReference(bookingReference);
        if (booking.status() != com.holidayresort.booking.booking.domain.BookingStatus.PENDING) {
            throw new PaymentException(PaymentException.Kind.CONFLICT,
                    "Booking is not in PENDING state, no payment needed.");
        }
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Payment payment = new Payment(paymentId, bookingReference,
                booking.price().totalMinor(), PaymentStatus.INITIATED,
                provider.providerName(), null, LocalDateTime.now(clock), null);
        Payment created = payments.save(payment);

        String externalId = provider.createPayment(created.paymentId(),
                created.amountMinor(), "Booking " + bookingReference);
        created = payments.save(created.withProviderPaymentId(externalId));
        return created;
    }

    /** Handle async callback from the payment provider. */
    public Payment handleCallback(String providerPaymentId, boolean success) {
        Payment payment = payments.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new PaymentException(PaymentException.Kind.NOT_FOUND,
                        "Payment not found for provider id: " + providerPaymentId));
        if (payment.status() == PaymentStatus.SUCCEEDED) {
            return payment; // already processed, idempotent
        }
        if (success) {
            Payment paid = payments.save(payment.withStatus(PaymentStatus.SUCCEEDED));
            bookings.markPaid(payment.bookingReference());
            Booking booking = bookings.findByReference(payment.bookingReference());
            memberships.confirmPayment(booking.customerId(), LocalDateTime.now(clock));
            return paid;
        } else {
            payments.save(payment.withStatus(PaymentStatus.FAILED));
            bookings.markFailed(payment.bookingReference());
            return payment;
        }
    }

    /** Staff manually confirms a payment that is under review. */
    public Payment staffConfirmPaid(String paymentId) {
        Payment payment = payments.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentException.Kind.NOT_FOUND,
                        "Payment not found: " + paymentId));
        if (payment.status() != PaymentStatus.UNDER_REVIEW
                && payment.status() != PaymentStatus.INITIATED) {
            throw new PaymentException(PaymentException.Kind.CONFLICT,
                    "Only under-review or initiated payments can be confirmed.");
        }
        Payment paid = payments.save(payment.withStatus(PaymentStatus.SUCCEEDED));
        bookings.markPaid(payment.bookingReference());
        Booking booking = bookings.findByReference(payment.bookingReference());
        memberships.confirmPayment(booking.customerId(), LocalDateTime.now(clock));
        return paid;
    }

    /** Staff manually rejects a payment under review. */
    public Payment staffReject(String paymentId) {
        Payment payment = payments.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentException(PaymentException.Kind.NOT_FOUND,
                        "Payment not found: " + paymentId));
        if (payment.status() != PaymentStatus.UNDER_REVIEW) {
            throw new PaymentException(PaymentException.Kind.CONFLICT,
                    "Only under-review payments can be rejected.");
        }
        payments.save(payment.withStatus(PaymentStatus.FAILED));
        bookings.markFailed(payment.bookingReference());
        return payment;
    }
}
