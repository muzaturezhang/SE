package com.holidayresort.booking.persistence.payment;

import com.holidayresort.booking.payment.application.PaymentException;
import com.holidayresort.booking.payment.application.port.PaymentRepository;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentPurpose;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** D-owned MySQL adapter for C's payment port. */
@Repository
@Profile("mysql")
public class JdbcPaymentRepository implements PaymentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcPaymentRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<Payment> findById(String id) {
        return jdbcTemplate.query(SELECT + " WHERE id = ?", this::map, id).stream().findFirst();
    }

    @Override
    public Optional<Payment> findByBookingReference(String bookingReference) {
        return jdbcTemplate.query(SELECT + """
                WHERE booking_reference = ?
                ORDER BY created_at DESC
                LIMIT 1
                """, this::map, bookingReference).stream().findFirst();
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SELECT + " WHERE idempotency_key = ?", this::map,
                idempotencyKey).stream().findFirst();
    }

    @Override
    public List<Payment> findAll() {
        return jdbcTemplate.query(SELECT + " ORDER BY created_at DESC", this::map);
    }

    @Override
    public Payment save(Payment payment) {
        Optional<Payment> previous = findById(payment.id());
        Timestamp now = Timestamp.from(clock.instant());
        try {
            if (previous.isEmpty()) {
                String customerId = payment.customerId();
                if (customerId == null && payment.bookingReference() != null) {
                    customerId = jdbcTemplate.queryForObject("""
                            SELECT customer_id FROM booking WHERE reference = ?
                            """, String.class, payment.bookingReference());
                }
                jdbcTemplate.update("""
                        INSERT INTO payment (
                            id, purpose, customer_id, booking_reference, amount_minor,
                            currency, state, provider_reference, failure_reason,
                            idempotency_key, paid_at, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, 'CNY', ?, ?, ?, ?, ?, ?, ?, 0)
                        """, payment.id(), payment.purpose().name(), customerId,
                        payment.bookingReference(), payment.amountMinor(), payment.status().name(),
                        payment.providerReference(), payment.failureReason(),
                        payment.idempotencyKey(), timestamp(payment.paidAt()),
                        timestamp(payment.createdAt()), now);
            } else {
                jdbcTemplate.update("""
                        UPDATE payment
                        SET state = ?, provider_reference = ?, failure_reason = ?,
                            paid_at = ?, updated_at = ?, version = version + 1
                        WHERE id = ?
                        """, payment.status().name(), payment.providerReference(),
                        payment.failureReason(), timestamp(payment.paidAt()), now, payment.id());
            }
        } catch (DataIntegrityViolationException exception) {
            throw new PaymentException(PaymentException.Kind.CONFLICT, "payment",
                    "Payment could not be saved because its identifier or idempotency key already exists.");
        }
        return payment;
    }

    private Payment map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new Payment(
                rs.getString("id"), rs.getString("booking_reference"),
                rs.getString("idempotency_key"), rs.getLong("amount_minor"),
                PaymentStatus.valueOf(rs.getString("state")),
                rs.getString("provider_reference"), rs.getString("failure_reason"),
                toLocalDateTime(rs, "created_at"), toLocalDateTime(rs, "paid_at"),
                PaymentPurpose.valueOf(rs.getString("purpose")), rs.getString("customer_id"));
    }

    private static final String SELECT = """
            SELECT id, booking_reference, idempotency_key, amount_minor, state,
                   provider_reference, failure_reason, created_at, paid_at, purpose, customer_id
            FROM payment
            """;

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
