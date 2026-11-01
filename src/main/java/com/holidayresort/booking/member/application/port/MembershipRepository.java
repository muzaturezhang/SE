package com.holidayresort.booking.member.application.port;

import com.holidayresort.booking.member.domain.Membership;
import java.util.Optional;

/** A-owned persistence port for membership records. D supplies the production adapter. */
public interface MembershipRepository {

    Optional<Membership> findByCustomerId(String customerId);

    Membership save(Membership membership);
}
