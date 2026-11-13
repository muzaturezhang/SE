package com.holidayresort.booking.member.application;

import com.holidayresort.booking.config.MembershipProperties;
import com.holidayresort.booking.member.application.MemberAccountException.Kind;
import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.application.port.MembershipRepository;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.member.domain.Membership;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * A-owned membership rules. Applications stay PENDING until the payment
 * service confirms them; discount eligibility is derived from valid dates.
 */
@Service
public class MembershipService {

    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final MembershipProperties properties;
    private final Clock clock;

    public MembershipService(MemberRepository members, MembershipRepository memberships,
            MembershipProperties properties, Clock clock) {
        this.members = members;
        this.memberships = memberships;
        this.properties = properties;
        this.clock = clock;
    }

    public MembershipPlan planFor(String customerId) {
        requireCustomer(customerId);
        return new MembershipPlan(properties.feeMinor(), properties.discountPercent(), properties.durationDays());
    }

    public Optional<Membership> statusFor(String customerId) {
        requireCustomer(customerId);
        return memberships.findByCustomerId(customerId);
    }

    public DiscountResult discountFor(String customerId) {
        requireCustomer(customerId);
        Membership membership = memberships.findByCustomerId(customerId).orElse(null);
        if (membership == null) {
            return DiscountResult.inactive("未开通会员");
        }
        return switch (membership.stateOn(LocalDate.now(clock))) {
            case ACTIVE -> new DiscountResult(true, membership.discountPercent(),
                    "会员住宿 " + (100 - membership.discountPercent()) + " 折");
            case PENDING -> DiscountResult.inactive("会员待确认");
            case EXPIRED -> DiscountResult.inactive("会员已过期");
        };
    }

    /** Records an application with the current fee snapshot; idempotent while unconfirmed. */
    public Membership requestApplication(String customerId) {
        requireCustomer(customerId);
        Membership current = memberships.findByCustomerId(customerId).orElse(null);
        if (current != null && !current.paymentConfirmed()) {
            return current;
        }
        if (current != null && current.stateOn(LocalDate.now(clock)) == Membership.State.ACTIVE) {
            throw new MemberAccountException(Kind.CONFLICT, "membership", "会员仍在有效期内，无需重复开通。");
        }
        return memberships.save(new Membership(customerId, false, properties.feeMinor(),
                properties.discountPercent(), null, null));
    }

    /** Called by the payment owner after its verification succeeds; idempotent. */
    public Membership confirmPayment(String customerId, LocalDate paidOn) {
        requireCustomer(customerId);
        Membership application = memberships.findByCustomerId(customerId)
                .orElseThrow(() -> new MemberAccountException(Kind.NOT_FOUND, "membership", "没有待确认的会员申请。"));
        if (application.paymentConfirmed()) {
            return application;
        }
        LocalDate start = paidOn;
        LocalDate end = paidOn.plusDays(properties.durationDays() - 1);
        return memberships.save(new Membership(customerId, true, application.feeMinor(),
                application.discountPercent(), start, end));
    }

    private Customer requireCustomer(String customerId) {
        return members.findById(customerId)
                .orElseThrow(() -> new MemberAccountException(Kind.NOT_FOUND, "customer", "账户不存在。"));
    }
}
