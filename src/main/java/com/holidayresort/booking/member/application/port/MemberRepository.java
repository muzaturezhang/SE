package com.holidayresort.booking.member.application.port;

import com.holidayresort.booking.member.domain.Customer;
import java.util.Optional;

/** A-owned persistence port for customers. D supplies the production adapter. */
public interface MemberRepository {

    Optional<Customer> findByEmail(String normalizedEmail);

    Optional<Customer> findById(String id);

    Customer save(Customer customer);
}
