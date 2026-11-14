package com.holidayresort.booking.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.member.application.RegistrationService;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.member.infrastructure.InMemoryMemberRepository;
import com.holidayresort.booking.member.infrastructure.InMemoryMembershipRepository;
import com.holidayresort.booking.payment.domain.PaymentPurpose;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import com.holidayresort.booking.payment.infrastructure.InMemoryPaymentRepository;
import com.holidayresort.booking.payment.infrastructure.SandboxPaymentProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class MembershipPaymentServiceTest {

    @Test
    void failedMembershipPaymentDoesNotActivateMembership() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T04:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        var members = new InMemoryMemberRepository();
        var memberships = new InMemoryMembershipRepository();
        var registration = new RegistrationService(members, new BCryptPasswordEncoder(), clock);
        var membershipService = new MembershipService(members, memberships,
                new com.holidayresort.booking.config.MembershipProperties(68000, 5, 365), clock);
        Customer customer = registration.register("Failed Member", "failed-member@example.com", "safePass1");
        var payments = new InMemoryPaymentRepository();
        var provider = new SandboxPaymentProvider();
        BookingService bookings = mock(BookingService.class);
        var transactions = new PaymentTransactionService(payments, bookings, clock, membershipService);
        var service = new PaymentService(payments, provider, bookings, clock,
                membershipService, transactions);
        var payment = service.initiateMembershipPayment(customer.id());
        provider.simulateFailure(payment.providerReference(), "declined");

        var failed = service.handleCallback(payment.providerReference());

        assertThat(failed.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(membershipService.statusFor(customer.id()))
                .hasValueSatisfying(membership -> assertThat(membership.paymentConfirmed()).isFalse());
    }

    @Test
    void membershipFeeIsSeparateAndActivatesMembershipOnce() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-14T04:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        var members = new InMemoryMemberRepository();
        var memberships = new InMemoryMembershipRepository();
        var registration = new RegistrationService(members, new BCryptPasswordEncoder(), clock);
        var membershipService = new MembershipService(members, memberships,
                new com.holidayresort.booking.config.MembershipProperties(68000, 5, 365), clock);
        Customer customer = registration.register("Member Guest", "member@example.com", "safePass1");
        var payments = new InMemoryPaymentRepository();
        var provider = new SandboxPaymentProvider();
        BookingService bookings = mock(BookingService.class);
        var transactions = new PaymentTransactionService(payments, bookings, clock, membershipService);
        var service = new PaymentService(payments, provider, bookings, clock,
                membershipService, transactions);

        var payment = service.initiateMembershipPayment(customer.id());
        var duplicateInitiation = service.initiateMembershipPayment(customer.id());
        assertThat(duplicateInitiation.id()).isEqualTo(payment.id());
        assertThat(payment.purpose()).isEqualTo(PaymentPurpose.MEMBERSHIP);
        assertThat(payment.bookingReference()).isNull();
        assertThat(payment.amountMinor()).isEqualTo(68000L);

        provider.simulateSuccess(payment.providerReference());
        var paid = service.handleCallback(payment.providerReference());
        var duplicate = service.handleCallback(payment.providerReference());

        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(duplicate.id()).isEqualTo(paid.id());
        assertThat(membershipService.statusFor(customer.id()))
                .hasValueSatisfying(membership -> {
                    assertThat(membership.paymentConfirmed()).isTrue();
                    assertThat(membership.feeMinor()).isEqualTo(68000L);
                });
    }
}
