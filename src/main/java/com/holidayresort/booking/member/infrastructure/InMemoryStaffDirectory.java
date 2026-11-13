package com.holidayresort.booking.member.infrastructure;

import com.holidayresort.booking.member.application.port.StaffDirectory;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** In-memory directory for team-provisioned STAFF identities. */
@Repository
@Profile("!mysql & (dev | test)")
public class InMemoryStaffDirectory implements StaffDirectory {

    private final Map<String, StaffRecord> byUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<StaffRecord> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username.strip()));
    }

    @Override
    public void add(StaffRecord record) {
        StaffRecord previous = byUsername.putIfAbsent(record.username(), record);
        if (previous != null) {
            throw new IllegalStateException("Staff username already provisioned: " + record.username());
        }
    }
}
