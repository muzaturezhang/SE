package com.holidayresort.booking.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaContractTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesTheFullVersionedSchemaAndSeedCatalogue() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'PUBLIC'
                ORDER BY table_name
                """, String.class);

        assertThat(tables).contains(
                "ROOM_TYPE", "ROOM", "ROOM_BLOCK", "CUSTOMER", "MEMBERSHIP",
                "BOOKING", "BOOKING_ROOM_ALLOCATION", "BOOKING_ROOM_NIGHT",
                "PAYMENT", "PAYMENT_EVENT", "REMINDER_SCHEDULE", "REMINDER_EVENT",
                "PERSISTENCE_LOCK", "ROOM_CHANGE_EVENT", "ROOM_TYPE_RATE_EVENT",
                "BOOKING_ROOM_NIGHT_HISTORY", "BOOKING_EVENT", "MEMBERSHIP_EVENT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_type", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room", Integer.class)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForList(
                "SELECT id FROM persistence_lock ORDER BY id", String.class))
                .containsExactly("BOOKING", "PAYMENT", "ROOM_INVENTORY");
    }

    @Test
    void roomNumberAndCaseInsensitiveEmailAreUnique() {
        insertCustomer("CUST-1", "guest@example.com", "guest@example.com");

        assertThatThrownBy(() -> insertCustomer(
                "CUST-2", "Guest@Example.com", "guest@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCustomer(
                "CUST-3", "other@example.com", "OTHER@EXAMPLE.COM"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO room (id, room_type_id, enabled, created_at, updated_at, version)
                VALUES ('C101', 'courtyard', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roomNightPrimaryKeyPreventsOverlappingAllocations() {
        String first = insertBooking("BK-1001");
        String second = insertBooking("BK-1002");
        insertAllocation(first, "C101");
        insertAllocation(second, "C102");

        jdbcTemplate.update("""
                INSERT INTO booking_room_night (room_id, stay_date, booking_reference)
                VALUES ('C101', DATE '2026-12-01', ?)
                """, first);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO booking_room_night (room_id, stay_date, booking_reference)
                VALUES ('C101', DATE '2026-12-01', ?)
                """, second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paymentIdempotencyAndPurposeConstraintsAreEnforced() {
        insertCustomer("CUST-4", "payer@example.com", "payer@example.com");
        String booking = insertBookingForCustomer("BK-1003", "CUST-4", "courtyard");

        jdbcTemplate.update("""
                INSERT INTO payment (
                    id, purpose, customer_id, booking_reference, amount_minor, currency,
                    state, idempotency_key, created_at, updated_at, version)
                VALUES ('PAY-1', 'BOOKING', 'CUST-4', ?, 10000, 'CNY',
                        'PENDING', 'idem-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, booking);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO payment (
                    id, purpose, customer_id, booking_reference, amount_minor, currency,
                    state, idempotency_key, created_at, updated_at, version)
                VALUES ('PAY-2', 'BOOKING', 'CUST-4', ?, 10000, 'CNY',
                        'PENDING', 'idem-1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, booking)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO payment (
                    id, purpose, customer_id, booking_reference, amount_minor, currency,
                    state, idempotency_key, created_at, updated_at, version)
                VALUES ('PAY-3', 'MEMBERSHIP', 'CUST-4', ?, 10000, 'CNY',
                        'PENDING', 'idem-2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, booking)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertCustomer(String id, String email, String normalized) {
        jdbcTemplate.update("""
                INSERT INTO customer (
                    id, display_name, email, email_normalized, password_hash,
                    registered_on, created_at, updated_at, version)
                VALUES (?, 'Guest', ?, ?, '$2a$10$abcdefghijklmnopqrstuv',
                        DATE '2026-01-01', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, email, normalized);
    }

    private String insertBooking(String reference) {
        insertCustomer("CUST-" + reference, reference + "@example.com",
                reference.toLowerCase() + "@example.com");
        return insertBookingForCustomer(reference, "CUST-" + reference, "courtyard");
    }

    private String insertBookingForCustomer(String reference, String customerId, String roomTypeId) {
        jdbcTemplate.update("""
                INSERT INTO booking (
                    reference, customer_id, room_type_id, guest_name, guest_email,
                    arrival_date, departure_date, room_count, base_rate_minor, nights,
                    subtotal_minor, discount_minor, discount_label, total_minor, currency,
                    state, payment_deadline, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Guest', 'guest@example.com',
                        DATE '2026-12-01', DATE '2026-12-03', 1, 10000, 2,
                        20000, 0, NULL, 20000, 'CNY', 'PENDING',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, reference, customerId, roomTypeId);
        return reference;
    }

    private void insertAllocation(String bookingReference, String roomId) {
        jdbcTemplate.update("""
                INSERT INTO booking_room_allocation (
                    booking_reference, room_id, allocated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """, bookingReference, roomId);
    }
}
