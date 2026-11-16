package com.holidayresort.booking.persistence.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayresort.booking.persistence.lock.JdbcPersistenceLockService;
import com.holidayresort.booking.room.application.port.RoomInventoryConflictException;
import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomBlock;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JdbcRoomInventoryRepositoryTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate persistenceTransactionTemplate;

    @Autowired
    JdbcPersistenceLockService locks;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    Clock clock;

    JdbcRoomInventoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcRoomInventoryRepository(
                jdbcTemplate, persistenceTransactionTemplate, locks, objectMapper, clock);
    }

    @Test
    void readsTheSeedCatalogueAndCombinesManualAndBookingOccupancy() {
        insertBookingWithAllocation("BK-2001", "C101", LocalDate.now(clock).plusDays(3),
                LocalDate.now(clock).plusDays(5));

        RoomInventory snapshot = repository.readSnapshot();

        assertThat(snapshot.roomTypes()).extracting(RoomType::id)
                .containsExactly("courtyard", "pool", "terrace");
        assertThat(snapshot.rooms()).hasSize(8);
        assertThat(snapshot.blocks()).anySatisfy(block -> {
            assertThat(block.roomId()).isEqualTo("C101");
            assertThat(block.period().arrival()).isEqualTo(LocalDate.now(clock).plusDays(3));
        });
    }

    @Test
    void persistsRateRoomAndEnabledChangesWithAuditHistory() {
        RoomType courtyard = repository.readSnapshot().roomTypes().stream()
                .filter(type -> type.id().equals("courtyard"))
                .findFirst().orElseThrow();

        repository.updateSnapshot(inventory -> {
            RoomType repriced = new RoomType(
                    courtyard.id(), courtyard.name(), courtyard.englishName(),
                    courtyard.description(), courtyard.imagePath(), courtyard.imageAlt(),
                    courtyard.features(), 123456);
            List<RoomType> types = inventory.roomTypes().stream()
                    .map(type -> type.id().equals(courtyard.id()) ? repriced : type)
                    .toList();
            List<Room> rooms = new ArrayList<>(inventory.rooms());
            rooms.add(new Room("C106", "pool", true));
            return new RoomInventory(types, rooms, inventory.blocks());
        });

        RoomInventory updated = repository.readSnapshot();
        assertThat(updated.roomTypes()).filteredOn(type -> type.id().equals("courtyard"))
                .extracting(RoomType::nightlyRateMinor).containsExactly(123456L);
        assertThat(updated.rooms()).anyMatch(room -> room.id().equals("C106"));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM room_type_rate_event
                WHERE room_type_id = 'courtyard' AND new_rate_minor = 123456
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM room_change_event
                WHERE room_id = 'C106' AND event_type = 'CREATED'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateRoomsAndChangesToBookingOwnedBlocks() {
        RoomInventory current = repository.readSnapshot();

        assertThatThrownBy(() -> repository.updateSnapshot(inventory -> {
            List<Room> rooms = new ArrayList<>(inventory.rooms());
            rooms.add(new Room("C101", "pool", true));
            return new RoomInventory(inventory.roomTypes(), rooms, inventory.blocks());
        })).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> repository.updateSnapshot(inventory ->
                new RoomInventory(
                        inventory.roomTypes(),
                        inventory.rooms(),
                        List.of(new RoomBlock(
                                "C101", new StayPeriod(
                                        LocalDate.now(clock).plusDays(1),
                                        LocalDate.now(clock).plusDays(2)))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Booking occupancy");

        assertThat(repository.readSnapshot().blocks()).isEqualTo(current.blocks());
    }

    private void insertBookingWithAllocation(
            String reference, String roomId, LocalDate arrival, LocalDate departure) {
        jdbcTemplate.update("""
                INSERT INTO customer (
                    id, display_name, email, email_normalized, password_hash,
                    registered_on, created_at, updated_at, version)
                VALUES ('CUST-2001', 'Guest', 'guest2001@example.com', 'guest2001@example.com',
                        '$2a$10$abcdefghijklmnopqrstuv', DATE '2026-01-01',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO booking (
                    reference, customer_id, room_type_id, guest_name, guest_email,
                    arrival_date, departure_date, room_count, base_rate_minor, nights,
                    subtotal_minor, discount_minor, discount_label, total_minor, currency,
                    state, payment_deadline, created_at, updated_at, version)
                VALUES (?, 'CUST-2001', 'courtyard', 'Guest', 'guest2001@example.com',
                        ?, ?, 1, 10000, 2, 20000, 0, NULL, 20000, 'CNY', 'PENDING',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, reference, arrival, departure);
        jdbcTemplate.update("""
                INSERT INTO booking_room_allocation (
                    booking_reference, room_id, allocated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                """, reference, roomId);
    }
}