package com.holidayresort.booking.member.infrastructure;

import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.domain.Customer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Development adapter with atomic single-process writes; D provides the MySQL adapter. */
@Repository
@Profile("!mysql & (dev | test)")
public class InMemoryMemberRepository implements MemberRepository {

    private final Map<String, Customer> byId = new ConcurrentHashMap<>();
    private final Map<String, Customer> byEmail = new ConcurrentHashMap<>();

    @Override
    public Optional<Customer> findByEmail(String normalizedEmail) {
        return Optional.ofNullable(byEmail.get(normalizedEmail.strip().toLowerCase(Locale.ROOT)));
    }

    @Override
    public Optional<Customer> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public synchronized Customer save(Customer customer) {
        Customer sameEmail = byEmail.get(customer.email());
        if (sameEmail != null && !sameEmail.id().equals(customer.id())) {
            throw new IllegalStateException("Duplicate customer email: " + customer.email());
        }
        byId.put(customer.id(), customer);
        byEmail.put(customer.email(), customer);
        return customer;
    }
}
