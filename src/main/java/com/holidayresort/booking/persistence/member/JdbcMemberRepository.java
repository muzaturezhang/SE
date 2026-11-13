package com.holidayresort.booking.persistence.member;

import com.holidayresort.booking.member.application.MemberAccountException;
import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.domain.Customer;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** D-owned MySQL adapter for A's customer port. */
@Repository
@Profile("mysql")
public class JdbcMemberRepository implements MemberRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcMemberRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<Customer> findByEmail(String normalizedEmail) {
        String email = normalizedEmail == null ? "" : normalizedEmail.strip().toLowerCase(Locale.ROOT);
        return jdbcTemplate.query("""
                SELECT id, display_name, email, password_hash, registered_on
                FROM customer WHERE email_normalized = ?
                """, (rs, rowNum) -> new Customer(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getObject("registered_on", LocalDate.class)), email)
                .stream().findFirst();
    }

    @Override
    public Optional<Customer> findById(String id) {
        return jdbcTemplate.query("""
                SELECT id, display_name, email, password_hash, registered_on
                FROM customer WHERE id = ?
                """, (rs, rowNum) -> new Customer(
                        rs.getString("id"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getObject("registered_on", LocalDate.class)), id)
                .stream().findFirst();
    }

    @Override
    public Customer save(Customer customer) {
        var existing = findById(customer.id());
        try {
            if (existing.isEmpty()) {
                jdbcTemplate.update("""
                        INSERT INTO customer (
                            id, display_name, email, email_normalized, password_hash,
                            registered_on, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """, customer.id(), customer.displayName(), customer.email(),
                        customer.email(), customer.passwordHash(),
                        Date.valueOf(customer.registeredOn()),
                        java.sql.Timestamp.from(clock.instant()),
                        java.sql.Timestamp.from(clock.instant()));
            } else {
                jdbcTemplate.update("""
                        UPDATE customer
                        SET display_name = ?, email = ?, email_normalized = ?,
                            password_hash = ?, registered_on = ?, updated_at = ?,
                            version = version + 1
                        WHERE id = ?
                        """, customer.displayName(), customer.email(), customer.email(),
                        customer.passwordHash(), Date.valueOf(customer.registeredOn()),
                        java.sql.Timestamp.from(clock.instant()), customer.id());
            }
        } catch (DataIntegrityViolationException exception) {
            throw new MemberAccountException(MemberAccountException.Kind.CONFLICT,
                    "email", "该邮箱已注册，请直接登录。");
        }
        return customer;
    }
}
