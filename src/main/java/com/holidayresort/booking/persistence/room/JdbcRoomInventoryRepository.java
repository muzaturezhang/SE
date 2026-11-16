package com.holidayresort.booking.persistence.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayresort.booking.persistence.lock.JdbcPersistenceLockService;
import com.holidayresort.booking.room.application.port.RoomInventoryConflictException;
import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomBlock;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MySQL/H2 persistence adapter for B's atomic room inventory port.
 *
 * <p>The adapter locks the shared room-inventory row before reading current state. All catalogue,
 * room and audit writes then commit in the same transaction. Historical rows are updated or
 * soft-disabled, never deleted.
 */
@Repository
@Profile("mysql")
public class JdbcRoomInventoryRepository implements RoomInventoryEditor {

    private final RowMapper<RoomType> roomTypeMapper = this::roomType;
    private static final RowMapper<Room> ROOM_MAPPER = JdbcRoomInventoryRepository::room;
    private static final RowMapper<RoomBlock> BLOCK_MAPPER = JdbcRoomInventoryRepository::block;
    private static final TypeReference<List<String>> FEATURES_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate readTransactionTemplate;
    private final TransactionTemplate writeTransactionTemplate;
    private final JdbcPersistenceLockService locks;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JdbcRoomInventoryRepository(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate persistenceTransactionTemplate,
            JdbcPersistenceLockService locks,
            ObjectMapper objectMapper,
            Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.readTransactionTemplate = new TransactionTemplate(persistenceTransactionTemplate.getTransactionManager());
        this.readTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.readTransactionTemplate.setReadOnly(true);
        this.writeTransactionTemplate = new TransactionTemplate(persistenceTransactionTemplate.getTransactionManager());
        this.writeTransactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.locks = locks;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public RoomInventory readSnapshot() {
        return readTransactionTemplate.execute(status -> readSnapshotInTransaction());
    }

    @Override
    public void updateSnapshot(UnaryOperator<RoomInventory> change) {
        Objects.requireNonNull(change, "change must not be null");
        try {
            writeTransactionTemplate.executeWithoutResult(status -> {
                locks.lockRoomInventory();
                RoomInventory current = readSnapshotInTransaction();
                RoomInventory next = Objects.requireNonNull(change.apply(current), "change returned null");
                if (!Objects.equals(current.blocks(), next.blocks())) {
                    throw new IllegalStateException(
                            "Booking occupancy cannot be changed through B's room inventory port");
                }
                persistTypes(current.roomTypes(), next.roomTypes());
                persistRooms(current.rooms(), next.rooms());
            });
        } catch (DataIntegrityViolationException exception) {
            throw new RoomInventoryConflictException(
                    "roomId", "房间数据发生并发冲突，请刷新后重试。");
        }
    }

    private RoomInventory readSnapshotInTransaction() {
        List<RoomType> types = jdbcTemplate.query("""
                SELECT id, name, english_name, description, image_path, image_alt,
                       features_json, nightly_rate_minor
                FROM room_type
                ORDER BY id
                """, roomTypeMapper);
        List<Room> rooms = jdbcTemplate.query("""
                SELECT id, room_type_id, enabled
                FROM room
                ORDER BY id
                """, ROOM_MAPPER);
        List<RoomBlock> blocks = jdbcTemplate.query("""
                SELECT b.room_id, b.arrival_date, b.departure_date
                FROM room_block b
                UNION ALL
                SELECT a.room_id, booking.arrival_date, booking.departure_date
                FROM booking_room_allocation a
                JOIN booking ON booking.reference = a.booking_reference
                WHERE a.released_at IS NULL
                ORDER BY room_id, arrival_date, departure_date
                """, BLOCK_MAPPER);
        return new RoomInventory(types, rooms, blocks);
    }

    private void persistTypes(List<RoomType> current, List<RoomType> next) {
        Map<String, RoomType> previousById = byTypeId(current);
        Set<String> nextIds = new HashSet<>();
        Instant now = clock.instant();
        for (RoomType type : next) {
            nextIds.add(type.id());
            RoomType previous = previousById.get(type.id());
            if (previous == null) {
                insertType(type, now);
            } else if (!sameType(previous, type)) {
                updateType(type, now);
                if (previous.nightlyRateMinor() != type.nightlyRateMinor()) {
                    jdbcTemplate.update("""
                            INSERT INTO room_type_rate_event (
                                room_type_id, previous_rate_minor, new_rate_minor, changed_at)
                            VALUES (?, ?, ?, ?)
                            """, type.id(), previous.nightlyRateMinor(), type.nightlyRateMinor(), now);
                }
            }
        }
        if (!nextIds.containsAll(previousById.keySet())) {
            throw new IllegalStateException("Room types cannot be physically deleted");
        }
    }

    private void persistRooms(List<Room> current, List<Room> next) {
        Map<String, Room> previousById = byRoomId(current);
        Set<String> nextIds = new HashSet<>();
        Instant now = clock.instant();
        for (Room room : next) {
            nextIds.add(room.id());
            Room previous = previousById.get(room.id());
            if (previous == null) {
                insertRoom(room, now);
            } else if (!previous.equals(room)) {
                updateRoom(room, now);
                insertRoomEvent(previous, room, now);
            }
        }
        if (!nextIds.containsAll(previousById.keySet())) {
            throw new IllegalStateException("Rooms cannot be physically deleted");
        }
    }

    private void insertType(RoomType type, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO room_type (
                    id, name, english_name, description, image_path, image_alt, features_json,
                    nightly_rate_minor, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, type.id(), type.name(), type.englishName(), type.description(), type.imagePath(),
                type.imageAlt(), featuresJson(type.features()), type.nightlyRateMinor(), now, now);
        jdbcTemplate.update("""
                INSERT INTO room_type_rate_event (
                    room_type_id, previous_rate_minor, new_rate_minor, changed_at)
                VALUES (?, NULL, ?, ?)
                """, type.id(), type.nightlyRateMinor(), now);
    }

    private void updateType(RoomType type, Instant now) {
        int updated = jdbcTemplate.update("""
                UPDATE room_type
                SET name = ?, english_name = ?, description = ?, image_path = ?, image_alt = ?,
                    features_json = ?, nightly_rate_minor = ?, updated_at = ?, version = version + 1
                WHERE id = ?
                """, type.name(), type.englishName(), type.description(), type.imagePath(),
                type.imageAlt(), featuresJson(type.features()), type.nightlyRateMinor(), now, type.id());
        if (updated != 1) {
            throw new IllegalStateException("Room type update lost its target: " + type.id());
        }
    }

    private void insertRoom(Room room, Instant now) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO room (id, room_type_id, enabled, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, 0)
                    """, room.id(), room.roomTypeId(), room.enabled(), now, now);
        } catch (DataIntegrityViolationException exception) {
            throw new RoomInventoryConflictException("roomId", "这个房号已存在，请使用其他房号。");
        }
        jdbcTemplate.update("""
                INSERT INTO room_change_event (
                    room_id, event_type, previous_room_type_id, new_room_type_id,
                    previous_enabled, new_enabled, changed_at)
                VALUES (?, 'CREATED', NULL, ?, NULL, ?, ?)
                """, room.id(), room.roomTypeId(), room.enabled(), now);
    }

    private void updateRoom(Room room, Instant now) {
        int updated = jdbcTemplate.update("""
                UPDATE room
                SET room_type_id = ?, enabled = ?, updated_at = ?, version = version + 1
                WHERE id = ?
                """, room.roomTypeId(), room.enabled(), now, room.id());
        if (updated != 1) {
            throw new IllegalStateException("Room update lost its target: " + room.id());
        }
    }

    private void insertRoomEvent(Room previous, Room next, Instant now) {
        jdbcTemplate.update("""
                INSERT INTO room_change_event (
                    room_id, event_type, previous_room_type_id, new_room_type_id,
                    previous_enabled, new_enabled, changed_at)
                VALUES (?, 'UPDATED', ?, ?, ?, ?, ?)
                """, next.id(), previous.roomTypeId(), next.roomTypeId(),
                previous.enabled(), next.enabled(), now);
    }

    private RoomType roomType(ResultSet resultSet, int rowNumber) throws SQLException {
        String json = resultSet.getString("features_json");
        try {
            return new RoomType(
                    resultSet.getString("id"),
                    resultSet.getString("name"),
                    resultSet.getString("english_name"),
                    resultSet.getString("description"),
                    resultSet.getString("image_path"),
                    resultSet.getString("image_alt"),
                    objectMapper.readValue(json, FEATURES_TYPE),
                    resultSet.getLong("nightly_rate_minor"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid room-type features JSON", exception);
        }
    }

    private static Room room(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Room(
                resultSet.getString("id"),
                resultSet.getString("room_type_id"),
                resultSet.getBoolean("enabled"));
    }

    private static RoomBlock block(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RoomBlock(
                resultSet.getString("room_id"),
                new StayPeriod(
                        resultSet.getObject("arrival_date", LocalDate.class),
                        resultSet.getObject("departure_date", LocalDate.class)));
    }

    private String featuresJson(List<String> features) {
        try {
            return objectMapper.writeValueAsString(features);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Features cannot be serialized", exception);
        }
    }

    private static boolean sameType(RoomType left, RoomType right) {
        return left.id().equals(right.id())
                && left.name().equals(right.name())
                && left.englishName().equals(right.englishName())
                && left.description().equals(right.description())
                && left.imagePath().equals(right.imagePath())
                && left.imageAlt().equals(right.imageAlt())
                && left.features().equals(right.features())
                && left.nightlyRateMinor() == right.nightlyRateMinor();
    }

    private static Map<String, RoomType> byTypeId(List<RoomType> types) {
        Map<String, RoomType> result = new HashMap<>();
        types.forEach(type -> result.put(type.id(), type));
        return result;
    }

    private static Map<String, Room> byRoomId(List<Room> rooms) {
        Map<String, Room> result = new HashMap<>();
        rooms.forEach(room -> result.put(room.id(), room));
        return result;
    }
}