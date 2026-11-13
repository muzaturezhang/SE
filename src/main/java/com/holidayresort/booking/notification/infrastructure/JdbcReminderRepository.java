package com.holidayresort.booking.notification.infrastructure;

import com.holidayresort.booking.notification.application.ReminderRepository;
import com.holidayresort.booking.notification.domain.ReminderDraft;
import com.holidayresort.booking.notification.domain.ReminderKind;
import com.holidayresort.booking.notification.domain.ReminderSchedule;
import com.holidayresort.booking.notification.domain.ReminderStatus;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC adapter for D's reminder schedule and delivery audit tables. */
@Repository
@Transactional
public class JdbcReminderRepository implements ReminderRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, booking_reference, recipient_email, check_in_date, check_out_date,
                   room_type_name, remind_on_date, scheduled_at, reminder_kind, status,
                   attempt_count, next_attempt_at, last_attempt_at, sent_at, last_error,
                   created_at, updated_at, version
            FROM reminder_schedule
            """;

    private static final String INSERT_SQL = """
            INSERT INTO reminder_schedule (
                booking_reference, recipient_email, check_in_date, check_out_date,
                room_type_name, remind_on_date, scheduled_at, reminder_kind, status,
                attempt_count, next_attempt_at, last_attempt_at, sent_at, last_error,
                created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final RowMapper<ReminderSchedule> ROW_MAPPER = JdbcReminderRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReminderSchedule upsert(ReminderDraft draft) {
        Optional<ReminderSchedule> existing =
                findForUpdate(draft.bookingReference(), draft.kind());
        if (existing.isEmpty()) {
            try {
                return insert(draft);
            } catch (DuplicateKeyException duplicate) {
                existing = findForUpdate(draft.bookingReference(), draft.kind());
                if (existing.isEmpty()) {
                    throw duplicate;
                }
            }
        }
        ReminderSchedule previous = existing.orElseThrow();
        if (sameRevision(previous, draft)) {
            return previous;
        }
        int updated = jdbcTemplate.update("""
                UPDATE reminder_schedule
                SET recipient_email = ?, check_in_date = ?, check_out_date = ?,
                    room_type_name = ?, remind_on_date = ?, scheduled_at = ?,
                    status = ?, attempt_count = 0, next_attempt_at = NULL,
                    last_attempt_at = NULL, sent_at = NULL, last_error = NULL,
                    updated_at = ?, version = version + 1
                WHERE id = ?
                """,
                draft.recipientEmail(),
                draft.checkInDate(),
                draft.checkOutDate(),
                draft.roomTypeName(),
                draft.remindOnDate(),
                timestamp(draft.scheduledAt()),
                ReminderStatus.PENDING.name(),
                timestamp(draft.createdAt()),
                previous.id());
        if (updated != 1) {
            throw new IllegalStateException("Reminder reschedule update lost its target");
        }
        recordEvent(previous.id(), "RESCHEDULED", previous, draft.createdAt());
        return findById(previous.id()).orElseThrow();
    }

    @Override
    public Optional<ReminderSchedule> findById(long id) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public Optional<ReminderSchedule> findByBookingReferenceAndKind(
            String bookingReference, ReminderKind kind) {
        return jdbcTemplate.query(
                        SELECT_COLUMNS + " WHERE booking_reference = ? AND reminder_kind = ?",
                        ROW_MAPPER,
                        bookingReference,
                        kind.name())
                .stream().findFirst();
    }

    @Override
    public List<ReminderSchedule> findDue(Instant now, LocalDate throughDate, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return jdbcTemplate.query(
                SELECT_COLUMNS + """
                         WHERE status = ?
                           AND (
                               (scheduled_at IS NOT NULL AND scheduled_at <= ?)
                               OR (scheduled_at IS NULL AND remind_on_date <= ?)
                           )
                           AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                         ORDER BY remind_on_date, id
                         LIMIT ?
                        """,
                ROW_MAPPER,
                ReminderStatus.PENDING.name(),
                timestamp(now),
                throughDate,
                timestamp(now),
                limit);
    }

    @Override
    public boolean cancelByBookingReference(String bookingReference, Instant updatedAt) {
        Optional<ReminderSchedule> existing =
                findForUpdate(bookingReference, ReminderKind.ONE_DAY);
        if (existing.isEmpty()) {
            return false;
        }
        ReminderSchedule previous = existing.orElseThrow();
        if (previous.status() == ReminderStatus.CANCELLED) {
            return true;
        }
        int updated = jdbcTemplate.update("""
                UPDATE reminder_schedule
                SET status = ?, next_attempt_at = NULL, updated_at = ?, version = version + 1
                WHERE id = ?
                """,
                ReminderStatus.CANCELLED.name(),
                timestamp(updatedAt),
                previous.id());
        if (updated != 1) {
            throw new IllegalStateException("Reminder cancellation update lost its target");
        }
        recordEvent(previous.id(), "CANCELLED", previous, updatedAt);
        return true;
    }

    @Override
    public boolean claimForSending(long id, long expectedVersion, Instant now) {
        boolean claimed = jdbcTemplate.update("""
                UPDATE reminder_schedule
                SET status = ?, attempt_count = attempt_count + 1,
                    last_attempt_at = ?, updated_at = ?, version = version + 1
                WHERE id = ? AND status = ? AND version = ?
                """,
                ReminderStatus.SENDING.name(),
                timestamp(now),
                timestamp(now),
                id,
                ReminderStatus.PENDING.name(),
                expectedVersion) == 1;
        if (claimed) {
            recordEvent(id, "SENDING", null, now);
        }
        return claimed;
    }

    @Override
    public boolean markSent(long id, long expectedVersion, Instant sentAt) {
        boolean updated = jdbcTemplate.update("""
                UPDATE reminder_schedule
                SET status = ?, sent_at = ?, next_attempt_at = NULL, last_error = NULL,
                    updated_at = ?, version = version + 1
                WHERE id = ? AND status = ? AND version = ?
                """,
                ReminderStatus.SENT.name(),
                timestamp(sentAt),
                timestamp(sentAt),
                id,
                ReminderStatus.SENDING.name(),
                expectedVersion) == 1;
        if (updated) {
            recordEvent(id, "SENT", null, sentAt);
        }
        return updated;
    }

    @Override
    public boolean recordFailure(
            long id,
            long expectedVersion,
            String errorCode,
            Instant nextAttemptAt,
            Instant updatedAt) {
        ReminderStatus nextStatus = nextAttemptAt == null ? ReminderStatus.FAILED : ReminderStatus.PENDING;
        boolean updated = jdbcTemplate.update("""
                UPDATE reminder_schedule
                SET status = ?, next_attempt_at = ?, last_error = ?, updated_at = ?,
                    version = version + 1
                WHERE id = ? AND status = ? AND version = ?
                """,
                nextStatus.name(),
                timestamp(nextAttemptAt),
                errorCode,
                timestamp(updatedAt),
                id,
                ReminderStatus.SENDING.name(),
                expectedVersion) == 1;
        if (updated) {
            recordEvent(id, nextAttemptAt == null ? "FAILED" : "RETRY_SCHEDULED", null, updatedAt);
        }
        return updated;
    }

    @Override
    public int recoverStaleSending(Instant cutoff, Instant now, int maxAttempts) {
        int exhausted = jdbcTemplate.update("""
                UPDATE reminder_schedule
                SET status = ?, next_attempt_at = NULL, last_error = ?, updated_at = ?, version = version + 1
                WHERE status = ? AND last_attempt_at < ? AND attempt_count >= ?
                """, ReminderStatus.FAILED.name(), "DELIVERY_LEASE_EXPIRED",
                timestamp(now), ReminderStatus.SENDING.name(), timestamp(cutoff), maxAttempts);
        int reclaimed = jdbcTemplate.update("""
                UPDATE reminder_schedule
                SET status = ?, next_attempt_at = ?, last_error = ?, updated_at = ?, version = version + 1
                WHERE status = ? AND last_attempt_at < ? AND attempt_count < ?
                """, ReminderStatus.PENDING.name(), timestamp(now), "DELIVERY_LEASE_EXPIRED",
                timestamp(now), ReminderStatus.SENDING.name(), timestamp(cutoff), maxAttempts);
        return exhausted + reclaimed;
    }

    private ReminderSchedule insert(ReminderDraft draft) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, draft.bookingReference());
            statement.setString(2, draft.recipientEmail());
            statement.setObject(3, draft.checkInDate());
            statement.setObject(4, draft.checkOutDate());
            statement.setString(5, draft.roomTypeName());
            statement.setObject(6, draft.remindOnDate());
            statement.setTimestamp(7, timestamp(draft.scheduledAt()));
            statement.setString(8, draft.kind().name());
            statement.setString(9, ReminderStatus.PENDING.name());
            statement.setInt(10, 0);
            statement.setNull(11, Types.TIMESTAMP);
            statement.setNull(12, Types.TIMESTAMP);
            statement.setNull(13, Types.TIMESTAMP);
            statement.setNull(14, Types.VARCHAR);
            statement.setTimestamp(15, timestamp(draft.createdAt()));
            statement.setTimestamp(16, timestamp(draft.createdAt()));
            return statement;
        }, keyHolder);
        if (inserted != 1 || keyHolder.getKey() == null) {
            throw new IllegalStateException("Reminder insert did not return a generated key");
        }
        long id = keyHolder.getKey().longValue();
        recordEvent(id, "SCHEDULED", null, draft.createdAt());
        return findById(id).orElseThrow(() -> new IllegalStateException("Inserted reminder cannot be reloaded"));
    }

    private Optional<ReminderSchedule> findForUpdate(String bookingReference, ReminderKind kind) {
        return jdbcTemplate.query(
                        SELECT_COLUMNS + " WHERE booking_reference = ? AND reminder_kind = ? FOR UPDATE",
                        ROW_MAPPER,
                        bookingReference,
                        kind.name())
                .stream().findFirst();
    }

    private void recordEvent(long reminderId, String eventType, ReminderSchedule previous, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO reminder_event (
                    reminder_id, event_type, previous_check_in_date, previous_check_out_date,
                    previous_room_type_name, previous_scheduled_at, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                reminderId,
                eventType,
                previous == null ? null : previous.checkInDate(),
                previous == null ? null : previous.checkOutDate(),
                previous == null ? null : previous.roomTypeName(),
                previous == null ? null : timestamp(previous.scheduledAt()),
                timestamp(occurredAt));
    }

    private static boolean sameRevision(ReminderSchedule current, ReminderDraft draft) {
        return Objects.equals(current.recipientEmail(), draft.recipientEmail())
                && Objects.equals(current.checkInDate(), draft.checkInDate())
                && Objects.equals(current.checkOutDate(), draft.checkOutDate())
                && Objects.equals(current.roomTypeName(), draft.roomTypeName())
                && Objects.equals(current.remindOnDate(), draft.remindOnDate())
                && Objects.equals(current.scheduledAt(), draft.scheduledAt())
                && current.status() != ReminderStatus.CANCELLED;
    }

    private static ReminderSchedule mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        LocalDate checkIn = resultSet.getObject("check_in_date", LocalDate.class);
        LocalDate checkOut = resultSet.getObject("check_out_date", LocalDate.class);
        String roomType = resultSet.getString("room_type_name");
        Instant scheduledAt = instant(resultSet, "scheduled_at");
        LocalDate remindOn = resultSet.getObject("remind_on_date", LocalDate.class);
        return new ReminderSchedule(
                resultSet.getLong("id"),
                resultSet.getString("booking_reference"),
                resultSet.getString("recipient_email"),
                checkIn,
                checkOut == null ? checkIn.plusDays(1) : checkOut,
                roomType == null ? "未提供房型" : roomType,
                remindOn,
                scheduledAt == null ? remindOn.atStartOfDay(ZoneOffset.UTC).toInstant() : scheduledAt,
                ReminderKind.valueOf(resultSet.getString("reminder_kind")),
                ReminderStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt_count"),
                instant(resultSet, "next_attempt_at"),
                instant(resultSet, "last_attempt_at"),
                instant(resultSet, "sent_at"),
                resultSet.getString("last_error"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                resultSet.getLong("version"));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
