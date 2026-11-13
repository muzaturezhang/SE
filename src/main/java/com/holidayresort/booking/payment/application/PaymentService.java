package com.holidayresort.booking.payment.application;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.payment.application.PaymentException.Kind;
import com.holidayresort.booking.payment.application.port.PaymentProvider;
import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * C-owned payment orchestration. Provider I/O is deliberately outside the
 * database transaction; PaymentTransactionService owns the atomic state update.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final BookingService bookings;
    private final Clock clock;
    private final PaymentTransactionService transactions;

    /** Compatibility constructor for focused tests that use in-memory adapters. */
    public PaymentService(PaymentRepository payments, PaymentProvider provider,
                          BookingService bookings, Clock clock) {
        this(payments, provider, bookings, clock,
                new PaymentTransactionService(payments, bookings, clock));
    }

    @Autowired
    public PaymentService(PaymentRepository payments, PaymentProvider provider,
                          BookingService bookings, Clock clock,
                          PaymentTransactionService transactions) {
        this.payments = payments;
        this.provider = provider;
        this.bookings = bookings;
        this.clock = clock;
        this.transactions = transactions;
    }

    /**
     * Initiate payment for a booking. A live pending payment is returned; a
     * terminal or under-review booking/payment is never silently charged again.
     */
    public Payment initiatePayment(String bookingReference, String customerId) {
        Booking booking = bookings.findByReference(bookingReference);
        if (booking.status() == BookingStatus.PAID) {
            throw new PaymentException(Kind.CONFLICT, "booking", "Booking is already paid.");
        }
        if (booking.status() == BookingStatus.FAILED || booking.status() == BookingStatus.EXPIRED) {
            throw new PaymentException(Kind.CONFLICT, "booking",
                    "This booking is no longer available for payment.");
        }
        if (booking.status() == BookingStatus.UNDER_REVIEW) {
            throw new PaymentException(Kind.CONFLICT, "booking",
                    "The previous payment result is under review.");
        }
        var existing = payments.findByBookingReference(bookingReference);
        if (existing.isPresent()) {
            PaymentStatus status = existing.get().status();
            if (status == PaymentStatus.PENDING) {
                return existing.get();
            }
            if (status == PaymentStatus.PAID || status == PaymentStatus.UNDER_REVIEW) {
                throw new PaymentException(Kind.CONFLICT, "payment",
                        "An existing payment must be reconciled before a new attempt.");
            }
        }

        PaymentProvider.Initiation initiation = provider.initiate(
                bookingReference, booking.price().totalMinor(), customerId);
        return transactions.createPending(
                "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                bookingReference,
                "idem-" + bookingReference + "-" + UUID.randomUUID(),
                initiation.providerReference());
    }

    /** Handle a provider callback after the provider query has completed. */
    public Payment handleCallback(String providerReference) {
        Payment payment = findPayment(providerReference);
        if (payment.status().isTerminal()) {
            return payment;
        }
        PaymentProvider.Result result = provider.query(providerReference);
        return transactions.applyResult(providerReference, result);
    }

    /** Staff reconciliation: confirm a payment that is explicitly under review. */
    public Payment staffConfirmPaid(String providerReference) {
        return transactions.confirmUnderReview(providerReference);
    }

    /** Staff reconciliation: reject a payment that is explicitly under review. */
    public Payment staffReject(String providerReference, String reason) {
        return transactions.rejectUnderReview(providerReference, reason);
    }

    public Payment findByBookingReference(String bookingReference) {
        return payments.findByBookingReference(bookingReference)
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "booking",
                        "No payment record for this booking."));
    }

    private Payment findPayment(String providerReference) {
        return payments.findAll().stream()
                .filter(p -> providerReference.equals(p.providerReference()))
                .findFirst()
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "providerRef",
                        "Payment record not found."));
    }
}
