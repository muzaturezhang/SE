package com.holidayresort.booking.member.application.port;

import java.util.Optional;

/**
 * A-owned directory for provisioned STAFF identities. Staff accounts are
 * created by the team, never through public registration.
 */
public interface StaffDirectory {

    Optional<StaffRecord> findByUsername(String username);

    void add(StaffRecord record);

    record StaffRecord(String username, String passwordHash) {
        public StaffRecord {
            username = username.strip();
        }
    }
}
