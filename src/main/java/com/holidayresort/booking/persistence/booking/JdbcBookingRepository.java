package com.holidayresort.booking.persistence.booking;

import com.holidayresort.booking.booking.application.BookingException;
import com.holidayresort.booking.booking.application.port.BookingRepository;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.booking.domain.PriceSnapshot;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** D-owned MySQL adapter for C's booking port. */
@Repository
@Profile("mysql")
public class JdbcBookingRepository implements BookingRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcBookingRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public Optional<Booking> findByReference(String reference) {
        return jdbcTemplate.query("""
                SELECT reference, idempotency_key, customer_id, guest_name, guest_email,
                       room_type_id, arrival_date, departure_date, room_count,
                       base_rate_minor, nights, discount_percent, subtotal_minor,
                       discount_minor, total_minor, state, created_at, payment_deadline
                FROM booking WHERE reference = ?
                """, this::map, reference).stream().findFirst();
    }

    @Override
    public Optional<Booking> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT reference, idempotency_key, customer_id, guest_name, guest_email,
                       room_type_id, arrival_date, departure_date, room_count,
                       base_rate_minor, nights, discount_percent, subtotal_minor,
                       discount_minor, total_minor, state, created_at, payment_deadline
                FROM booking WHERE idempotency_key = ?
                """, this::map, idempotencyKey).stream().findFirst();
    }

    @Override
    public List<Booking> findByCustomerId(String customerId) {
        return jdbcTemplate.query("""
                SELECT reference, idempotency_key, customer_id, guest_name, guest_email,
                       room_type_id, arrival_date, departure_date, room_count,
                       base_rate_minor, nights, discount_percent, subtotal_minor,
                       discount_minor, total_minor, state, created_at, payment_deadline
                FROM booking WHERE customer_id = ? ORDER BY created_at DESC
                """, this::map, customerId);
    }

    @Override
    public List<Booking> findAll() {
        return jdbcTemplate.query("""
                SELECT reference, idempotency_key, customer_id, guest_name, guest_email,
                       room_type_id, arrival_date, departure_date, room_count,
                       base_rate_minor, nights, discount_percent, subtotal_minor,
                       discount_minor, total_minor, state, created_at, payment_deadline
                FROM booking ORDER BY created_at DESC
                """, this::map);
    }

    @Override
    public Booking save(Booking booking) {
        Optional<Booking> previous = findByReference(booking.reference());
        Timestamp now = Timestamp.from(clock.instant());
        try {
            if (previous.isEmpty()) {
                jdbcTemplate.update("""
                        INSERT INTO booking (
                            reference, idempotency_key, customer_id, room_type_id,
                            guest_name, guest_email, arrival_date, departure_date, room_count,
                            base_rate_minor, nights, discount_percent, subtotal_minor,
                            discount_minor, discount_label, total_minor, currency, state,
                            payment_deadline, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CNY', ?, ?, ?, ?, 0)
                        """, booking.reference(), booking.idempotencyKey(), booking.customerId(),
                        booking.roomTypeId(), booking.guestName(), booking.guestEmail(),
                        java.sql.Date.valueOf(booking.stay().arrival()),
                        java.sql.Date.valueOf(booking.stay().departure()), booking.roomCount(),
                        booking.price().nightlyRateMinor(), booking.price().nights(),
                        booking.price().discountPercent(), booking.price().subtotalMinor(),
                        booking.price().subtotalMinor() - booking.price().totalMinor(),
                        booking.price().discountPercent() > 0 ? "会员优惠" : null,
                        booking.price().totalMinor(), booking.status().name(),
                        timestamp(booking.paymentDeadline()), timestamp(booking.createdAt()), now);
                recordEvent(booking.reference(), "CREATED", null, booking.status().name(), now);
            } else {
                Booking old = previous.orElseThrow();
                jdbcTemplate.update("""
                        UPDATE booking
                        SET guest_name = ?, guest_email = ?, room_count = ?, state = ?,
                            subtotal_minor = ?, discount_percent = ?, discount_minor = ?,
                            total_minor = ?, payment_deadline = ?, updated_at = ?,
                            version = version + 1
                        WHERE reference = ?
                        """, booking.guestName(), booking.guestEmail(), booking.roomCount(),
                        booking.status().name(), booking.price().subtotalMinor(),
                        booking.price().discountPercent(),
                        booking.price().subtotalMinor() - booking.price().totalMinor(),
                        booking.price().totalMinor(), timestamp(booking.paymentDeadline()),
                        now, booking.reference());
                if (old.status() != booking.status()) {
                    recordEvent(booking.reference(), "STATUS_CHANGED", old.status().name(),
                            booking.status().name(), now);
                }
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BookingException(BookingException.Kind.CONFLICT, "booking",
                    "Booking could not be saved because its identifier or idempotency key already exists.");
        }
        return booking;
    }

    private Booking map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String reference = rs.getString("reference");
        List<String> roomIds = jdbcTemplate.queryForList("""
                SELECT room_id FROM booking_room_allocation
                WHERE booking_reference = ? AND released_at IS NULL
                ORDER BY room_id
                """, String.class, reference);
        long subtotal = rs.getLong("subtotal_minor");
        long total = rs.getLong("total_minor");
        int discountPercent = rs.getObject("discount_percent") == null
                ? (subtotal == 0 ? 0 : (int) Math.round((subtotal - total) * 100.0 / subtotal))
                : rs.getInt("discount_percent");
        return new Booking(
                reference,
                rs.getString("idempotency_key"),
                rs.getString("customer_id"),
                rs.getString("guest_name"),
                rs.getString("guest_email"),
                rs.getString("room_type_id"),
                new StayPeriod(
                        rs.getObject("arrival_date", java.time.LocalDate.class),
                        rs.getObject("departure_date", java.time.LocalDate.class)),
                rs.getInt("room_count"),
                roomIds,
                new PriceSnapshot(
                        rs.getLong("base_rate_minor"), rs.getLong("nights"),
                        rs.getInt("room_count"), discountPercent, subtotal, total),
                BookingStatus.valueOf(rs.getString("state")),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "payment_deadline"));
    }

    private void recordEvent(String reference, String type, String previous, String next, Timestamp now) {
        jdbcTemplate.update("""
                INSERT INTO booking_event (
                    booking_reference, event_type, previous_state, new_state, occurred_at)
                VALUES (?, ?, ?, ?, ?)
                """, reference, type, previous, next, now);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
