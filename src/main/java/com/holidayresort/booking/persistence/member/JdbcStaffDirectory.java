package com.holidayresort.booking.persistence.member;

import com.holidayresort.booking.member.application.port.StaffDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** D-owned MySQL adapter for team-provisioned STAFF identities. */
@Repository
@Profile("mysql")
public class JdbcStaffDirectory implements StaffDirectory {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStaffDirectory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StaffRecord> findByUsername(String username) {
        return jdbcTemplate.query("""
                SELECT username, password_hash
                FROM staff_account WHERE username = ? AND enabled = TRUE
                """, (rs, rowNum) -> new StaffRecord(rs.getString("username"),
                        rs.getString("password_hash")), username)
                .stream().findFirst();
    }

    @Override
    public void add(StaffRecord record) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO staff_account (
                        id, username, password_hash, enabled, created_at, updated_at, version)
                    VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                    """, UUID.randomUUID().toString(), record.username(), record.passwordHash());
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("Staff username already provisioned: " + record.username());
        }
    }
}
