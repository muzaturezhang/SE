package com.holidayresort.booking.member.infrastructure;

import com.holidayresort.booking.member.application.port.MembershipRepository;
import com.holidayresort.booking.member.domain.Membership;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Development adapter keeping the current record per customer; D provides history-aware storage. */
@Repository
@Profile("!mysql & (dev | test)")
public class InMemoryMembershipRepository implements MembershipRepository {

    private final Map<String, Membership> byCustomerId = new ConcurrentHashMap<>();

    @Override
    public Optional<Membership> findByCustomerId(String customerId) {
        return Optional.ofNullable(byCustomerId.get(customerId));
    }

    @Override
    public synchronized Membership save(Membership membership) {
        byCustomerId.put(membership.customerId(), membership);
        return membership;
    }
}
