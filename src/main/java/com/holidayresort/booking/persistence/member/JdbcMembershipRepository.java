package com.holidayresort.booking.persistence.member;

import com.holidayresort.booking.member.application.port.MembershipRepository;
import com.holidayresort.booking.member.domain.Membership;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** D-owned MySQL adapter for A's membership port with append-only history. */
@Repository
@Profile("mysql")
public class JdbcMembershipRepository implements MembershipRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcMembershipRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<Membership> findByCustomerId(String customerId) {
        return jdbcTemplate.query("""
                SELECT customer_id, payment_confirmed, fee_minor, discount_basis_points,
                       valid_from, valid_until
                FROM membership WHERE customer_id = ?
                """, (rs, rowNum) -> new Membership(
                        rs.getString("customer_id"),
                        rs.getBoolean("payment_confirmed"),
                        rs.getLong("fee_minor"),
                        rs.getInt("discount_basis_points") / 100,
                        rs.getObject("valid_from", LocalDate.class),
                        rs.getObject("valid_until", LocalDate.class)), customerId)
                .stream().findFirst();
    }

    @Override
    public Membership save(Membership membership) {
        Optional<Membership> previous = findByCustomerId(membership.customerId());
        java.sql.Timestamp now = java.sql.Timestamp.from(clock.instant());
        if (previous.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO membership (
                        customer_id, payment_confirmed, fee_minor, discount_basis_points,
                        valid_from, valid_until, confirmed_at, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """, membership.customerId(), membership.paymentConfirmed(),
                    membership.feeMinor(), membership.discountPercent() * 100,
                    date(membership.startDate()), date(membership.endDate()),
                    membership.paymentConfirmed() ? now : null, now, now);
        } else {
            jdbcTemplate.update("""
                    UPDATE membership
                    SET payment_confirmed = ?, fee_minor = ?, discount_basis_points = ?,
                        valid_from = ?, valid_until = ?, confirmed_at = ?, updated_at = ?,
                        version = version + 1
                    WHERE customer_id = ?
                    """, membership.paymentConfirmed(), membership.feeMinor(),
                    membership.discountPercent() * 100, date(membership.startDate()),
                    date(membership.endDate()), membership.paymentConfirmed() ? now : null,
                    now, membership.customerId());
        }
        recordEvent(previous.orElse(null), membership, now);
        return membership;
    }

    private void recordEvent(Membership previous, Membership next, java.sql.Timestamp now) {
        jdbcTemplate.update("""
                INSERT INTO membership_event (
                    customer_id, event_type, previous_payment_confirmed,
                    new_payment_confirmed, previous_valid_from, previous_valid_until,
                    new_valid_from, new_valid_until, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, next.customerId(),
                previous == null ? "APPLIED" : next.paymentConfirmed() ? "CONFIRMED" : "UPDATED",
                previous == null ? null : previous.paymentConfirmed(),
                next.paymentConfirmed(),
                previous == null ? null : date(previous.startDate()),
                previous == null ? null : date(previous.endDate()),
                date(next.startDate()), date(next.endDate()), now);
    }

    private static java.sql.Date date(LocalDate value) {
        return value == null ? null : java.sql.Date.valueOf(value);
    }
}
