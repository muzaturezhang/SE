package com.holidayresort.booking.payment.application;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.member.application.MembershipPlan;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.payment.application.PaymentException.Kind;
import com.holidayresort.booking.payment.application.port.PaymentProvider;
import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentPurpose;
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
    private final MembershipService memberships;
    private final Clock clock;
    private final PaymentTransactionService transactions;

    /** Compatibility constructor for focused booking-only tests. */
    public PaymentService(PaymentRepository payments, PaymentProvider provider,
                          BookingService bookings, Clock clock) {
        this(payments, provider, bookings, clock, null,
                new PaymentTransactionService(payments, bookings, clock));
    }

    @Autowired
    public PaymentService(PaymentRepository payments, PaymentProvider provider,
                          BookingService bookings, Clock clock, MembershipService memberships,
                          PaymentTransactionService transactions) {
        this.payments = payments;
        this.provider = provider;
        this.bookings = bookings;
        this.memberships = memberships;
        this.clock = clock;
        this.transactions = transactions;
    }

    /** Initiate a stay payment; a live pending payment is returned idempotently. */
    public Payment initiatePayment(String bookingReference, String customerId) {
        Booking booking = bookings.findByReference(bookingReference);
        if (booking.status() == BookingStatus.PAID) {
            throw new PaymentException(Kind.CONFLICT, "booking", "Booking is already paid.");
        }
        if (booking.status() == BookingStatus.FAILED || booking.status() == BookingStatus.EXPIRED
                || booking.status() == BookingStatus.CANCELLED) {
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

    /**
     * Initiate the separate annual membership fee payment. Membership fee is
     * never applied to a booking discount and never occupies room inventory.
     */
    public Payment initiateMembershipPayment(String customerId) {
        if (memberships == null) {
            throw new PaymentException(Kind.CONFLICT, "membership",
                    "Membership service is not configured.");
        }
        MembershipPlan plan = memberships.planFor(customerId);
        var existing = payments.findByCustomerIdAndPurpose(customerId, PaymentPurpose.MEMBERSHIP);
        if (existing.isPresent()) {
            PaymentStatus status = existing.get().status();
            if (status == PaymentStatus.PENDING) {
                return existing.get();
            }
            if (status == PaymentStatus.PAID || status == PaymentStatus.UNDER_REVIEW) {
                throw new PaymentException(Kind.CONFLICT, "membershipPayment",
                        "The membership payment must be reconciled before a new attempt.");
            }
        }
        memberships.requestApplication(customerId);
        PaymentProvider.Initiation initiation = provider.initiate(
                "MEMBERSHIP-" + customerId, plan.feeMinor(), customerId);
        return transactions.createMembershipPending(
                "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                customerId, plan.feeMinor(),
                "member-" + UUID.randomUUID(),
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

    public Payment staffConfirmPaid(String providerReference) {
        return transactions.confirmUnderReview(providerReference);
    }

    public Payment staffReject(String providerReference, String reason) {
        return transactions.rejectUnderReview(providerReference, reason);
    }

    public Payment findByBookingReference(String bookingReference) {
        return payments.findByBookingReference(bookingReference)
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "booking",
                        "No payment record for this booking."));
    }

    public Payment findMembershipPayment(String customerId) {
        return payments.findByCustomerIdAndPurpose(customerId, PaymentPurpose.MEMBERSHIP)
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "customer",
                        "No membership payment record for this customer."));
    }

    private Payment findPayment(String providerReference) {
        return payments.findAll().stream()
                .filter(p -> providerReference.equals(p.providerReference()))
                .findFirst()
                .orElseThrow(() -> new PaymentException(Kind.NOT_FOUND, "providerRef",
                        "Payment record not found."));
    }
}
