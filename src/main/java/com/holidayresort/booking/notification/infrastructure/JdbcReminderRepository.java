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
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** JDBC adapter for D's notification schedule table. */
@Repository
public class JdbcReminderRepository implements ReminderRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, booking_reference, recipient_email, check_in_date, remind_on_date,
                   reminder_kind, status, attempt_count, next_attempt_at, last_attempt_at,
                   sent_at, last_error, created_at, updated_at, version
            FROM reminder_schedule
            """;

    private static final String INSERT_SQL = """
            INSERT INTO reminder_schedule (
                booking_reference, recipient_email, check_in_date, remind_on_date,
                reminder_kind, status, attempt_count, next_attempt_at, last_attempt_at,
                sent_at, last_error, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final RowMapper<ReminderSchedule> ROW_MAPPER = JdbcReminderRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ReminderSchedule insertIfAbsent(ReminderDraft draft) {
        try {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int inserted = jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        INSERT_SQL, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, draft.bookingReference());
                statement.setString(2, draft.recipientEmail());
                statement.setObject(3, draft.checkInDate());
                statement.setObject(4, draft.remindOnDate());
                statement.setString(5, draft.kind().name());
                statement.setString(6, ReminderStatus.PENDING.name());
                statement.setInt(7, 0);
                statement.setNull(8, Types.TIMESTAMP);
                statement.setNull(9, Types.TIMESTAMP);
                statement.setNull(10, Types.TIMESTAMP);
                statement.setNull(11, Types.VARCHAR);
                statement.setTimestamp(12, timestamp(draft.createdAt()));
                statement.setTimestamp(13, timestamp(draft.createdAt()));
                return statement;
            }, keyHolder);
            if (inserted != 1 || keyHolder.getKey() == null) {
                throw new IllegalStateException("Reminder insert did not return a generated key");
            }
            return findById(keyHolder.getKey().longValue())
                    .orElseThrow(() -> new IllegalStateException("Inserted reminder cannot be reloaded"));
        } catch (DuplicateKeyException duplicate) {
            return findByBookingReferenceAndKind(draft.bookingReference(), draft.kind())
                    .orElseThrow(() -> new IllegalStateException(
                            "Duplicate reminder was reported but the existing row was not found", duplicate));
        }
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
                           AND remind_on_date <= ?
                           AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                         ORDER BY remind_on_date, id
                         LIMIT ?
                        """,
                ROW_MAPPER,
                ReminderStatus.PENDING.name(),
                throughDate,
                timestamp(now),
                limit);
    }

    @Override
    public boolean claimForSending(long id, long expectedVersion, Instant now) {
        return jdbcTemplate.update("""
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
    }

    @Override
    public boolean markSent(long id, long expectedVersion, Instant sentAt) {
        return jdbcTemplate.update("""
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
    }

    @Override
    public boolean recordFailure(
            long id,
            long expectedVersion,
            String error,
            Instant nextAttemptAt,
            Instant updatedAt) {
        ReminderStatus nextStatus = nextAttemptAt == null ? ReminderStatus.FAILED : ReminderStatus.PENDING;
        return jdbcTemplate.update("""
                UPDATE reminder_schedule
                SET status = ?, next_attempt_at = ?, last_error = ?, updated_at = ?,
                    version = version + 1
                WHERE id = ? AND status = ? AND version = ?
                """,
                nextStatus.name(),
                timestamp(nextAttemptAt),
                error,
                timestamp(updatedAt),
                id,
                ReminderStatus.SENDING.name(),
                expectedVersion) == 1;
    }

    private static ReminderSchedule mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ReminderSchedule(
                resultSet.getLong("id"),
                resultSet.getString("booking_reference"),
                resultSet.getString("recipient_email"),
                resultSet.getObject("check_in_date", LocalDate.class),
                resultSet.getObject("remind_on_date", LocalDate.class),
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
