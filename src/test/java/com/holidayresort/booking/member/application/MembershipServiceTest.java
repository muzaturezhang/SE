package com.holidayresort.booking.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.holidayresort.booking.config.MembershipProperties;
import com.holidayresort.booking.member.application.MemberAccountException.Kind;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.member.domain.Membership;
import com.holidayresort.booking.member.domain.Membership.State;
import com.holidayresort.booking.member.infrastructure.InMemoryMemberRepository;
import com.holidayresort.booking.member.infrastructure.InMemoryMembershipRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class MembershipServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private InMemoryMemberRepository members;
    private InMemoryMembershipRepository memberships;
    private MembershipService service;
    private Customer customer;

    @BeforeEach
    void setup() {
        members = new InMemoryMemberRepository();
        memberships = new InMemoryMembershipRepository();
        customer = new RegistrationService(members, new BCryptPasswordEncoder(), clock)
                .register("林晓", "lin.xiao@example.com", "safePass1");
        service = new MembershipService(members, memberships, new MembershipProperties(68000, 5, 365), clock);
    }

    @Test
    void noMembershipMeansInactiveDiscountAndPublishedPlan() {
        assertThat(service.statusFor(customer.id())).isEmpty();
        assertThat(service.discountFor(customer.id())).isEqualTo(new DiscountResult(false, 0, "未开通会员"));
        assertThat(service.planFor(customer.id())).isEqualTo(new MembershipPlan(68000, 5, 365));
    }

    @Test
    void pendingApplicationIsIdempotentAndInactive() {
        Membership first = service.requestApplication(customer.id());
        assertThat(first.paymentConfirmed()).isFalse();
        assertThat(first.stateOn(LocalDate.of(2026, 11, 1))).isEqualTo(State.PENDING);
        assertThat(service.requestApplication(customer.id())).isEqualTo(first);
        assertThat(service.discountFor(customer.id())).isEqualTo(new DiscountResult(false, 0, "会员待确认"));
    }

    @Test
    void confirmedMembershipIsActiveForOneYearThenExpires() {
        service.requestApplication(customer.id());
        Membership active = service.confirmPayment(customer.id(), LocalDate.of(2026, 11, 1));
        assertThat(active.startDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(active.endDate()).isEqualTo(LocalDate.of(2027, 10, 31));
        assertThat(active.stateOn(LocalDate.of(2027, 10, 31))).isEqualTo(State.ACTIVE);
        assertThat(active.stateOn(LocalDate.of(2027, 11, 1))).isEqualTo(State.EXPIRED);
        assertThat(service.discountFor(customer.id())).isEqualTo(new DiscountResult(true, 5, "会员住宿 95 折"));
        assertThat(service.confirmPayment(customer.id(), LocalDate.of(2026, 12, 1))).isEqualTo(active);
    }

    @Test
    void activeMembershipCannotBeReappliedAndExpiredOneCanBeRenewed() {
        service.requestApplication(customer.id());
        service.confirmPayment(customer.id(), LocalDate.of(2026, 11, 1));
        assertThatThrownBy(() -> service.requestApplication(customer.id()))
                .isInstanceOfSatisfying(MemberAccountException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(Kind.CONFLICT));
        Membership expired = memberships.save(new Membership(customer.id(), true, 68000, 5,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        assertThat(expired.stateOn(LocalDate.of(2026, 11, 1))).isEqualTo(State.EXPIRED);
        Membership renewed = service.requestApplication(customer.id());
        assertThat(renewed.paymentConfirmed()).isFalse();
        assertThat(renewed.feeMinor()).isEqualTo(68000);
    }

    @Test
    void unknownCustomerOrMissingApplicationFailsReadably() {
        assertThatThrownBy(() -> service.discountFor("missing")).isInstanceOfSatisfying(MemberAccountException.class,
                exception -> assertThat(exception.kind()).isEqualTo(Kind.NOT_FOUND));
        assertThatThrownBy(() -> service.confirmPayment(customer.id(), LocalDate.of(2026, 11, 1)))
                .isInstanceOfSatisfying(MemberAccountException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(Kind.NOT_FOUND));
    }
}
