package com.holidayresort.booking.room.application;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomBlock;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.StayPeriod;
import com.holidayresort.booking.room.infrastructure.InMemoryRoomRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

class RoomManagementServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private InMemoryRoomRepository repository;
    private RoomManagementService management;
    private RoomQueryService queries;

    @BeforeEach
    void setup() {
        repository = new InMemoryRoomRepository(new ClassPathResource("fixtures/room-inventory.json"),
                new ObjectMapper(), clock);
        management = new RoomManagementService(repository, clock);
        queries = new DefaultRoomQueryService(repository, clock);
    }

    @Test
    void mutationsImmediatelyChangeQueriesAndPreserveOldSnapshots() {
        RoomInventory previous = repository.readSnapshot();
        assertThat(available("courtyard")).isEqualTo(4);
        management.create(" C106 ", "courtyard", true);
        assertThat(available("courtyard")).isEqualTo(5);
        management.update("C106", "pool", true);
        assertThat(available("courtyard")).isEqualTo(4);
        assertThat(available("pool")).isEqualTo(2);
        management.disable("C106");
        assertThat(available("pool")).isEqualTo(1);
        management.update("C106", "pool", true);
        assertThat(available("pool")).isEqualTo(2);
        assertThat(previous.rooms()).hasSize(8);
        assertThat(repository.readSnapshot().blocks()).isEqualTo(previous.blocks());
        assertThat(management.get("C106").room().enabled()).isTrue();
    }

    @Test
    void disabledCreationIsExcludedAndListFiltersAreApplied() {
        management.create("C106", "courtyard", false);
        assertThat(available("courtyard")).isEqualTo(4);
        assertThat(management.list("courtyard", "disabled")).extracting(item -> item.room().id())
                .containsExactly("C105", "C106");
        assertThat(management.list("pool", "enabled")).hasSize(1);
        assertThat(management.list("pool", "disabled")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"c106", "C 106", "-106", "THIS-ROOM-ID-IS-TOO-LONG", "<script>"})
    void invalidRoomIdentifiersNeverMutate(String id) {
        RoomInventory previous = repository.readSnapshot();
        assertThatThrownBy(() -> management.create(id, "courtyard", true))
                .isInstanceOf(RoomManagementException.class);
        assertThat(repository.readSnapshot()).isSameAs(previous);
    }

    @Test
    void duplicateUnknownTypeAndMissingRoomLeaveInventoryUnchanged() {
        RoomInventory previous = repository.readSnapshot();
        assertThatThrownBy(() -> management.create("C101", "courtyard", true))
                .isInstanceOfSatisfying(RoomManagementException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(RoomManagementException.Kind.CONFLICT));
        assertThatThrownBy(() -> management.create("C106", "unknown", true)).isInstanceOf(RoomManagementException.class);
        assertThatThrownBy(() -> management.update("C104", "unknown", false)).isInstanceOf(RoomManagementException.class);
        assertThatThrownBy(() -> management.disable("missing"))
                .isInstanceOfSatisfying(RoomManagementException.class,
                        exception -> assertThat(exception.kind()).isEqualTo(RoomManagementException.Kind.NOT_FOUND));
        assertThatThrownBy(() -> management.list("unknown", "all")).isInstanceOf(RoomManagementException.class);
        assertThatThrownBy(() -> management.list("", "wrong")).isInstanceOf(RoomManagementException.class);
        assertThat(repository.readSnapshot()).isSameAs(previous);
    }

    @ParameterizedTest
    @CsvSource({"-2, 0, false", "-2, 1, true", "0, 1, true", "2, 4, true"})
    void currentAndFutureBlocksGuardDisableAndTypeChangeButCheckoutTodayIsFinished(int start, int end, boolean guarded) {
        LocalDate today = LocalDate.now(clock);
        repository.updateSnapshot(inventory -> new RoomInventory(inventory.roomTypes(), inventory.rooms(), List.of(
                new RoomBlock("C104", new StayPeriod(today.plusDays(start), today.plusDays(end))))));
        RoomInventory previous = repository.readSnapshot();
        assertThat(management.get("C104").activeOrFutureBlock()).isEqualTo(guarded);
        if (guarded) {
            assertThatThrownBy(() -> management.disable("C104")).isInstanceOf(RoomManagementException.class);
            assertThatThrownBy(() -> management.update("C104", "courtyard", false)).isInstanceOf(RoomManagementException.class);
            assertThatThrownBy(() -> management.update("C104", "pool", true)).isInstanceOf(RoomManagementException.class);
            assertThat(repository.readSnapshot()).isSameAs(previous);
            management.update("C104", "courtyard", true); // A no-op is still permitted.
        } else {
            management.update("C104", "pool", true);
            management.disable("C104");
            assertThat(management.get("C104").room()).isEqualTo(new Room("C104", "pool", false));
        }
        assertThat(repository.readSnapshot().blocks()).isEqualTo(previous.blocks());
    }

    @Test
    void rateUsesExactMinorUnitsAndImmediatelyChangesQueryPrice() {
        management.updateRate("courtyard", new BigDecimal("1234.56"));
        assertThat(queries.listRoomTypes().get(0).nightlyRateMinor()).isEqualTo(123456);
        assertThat(queries.search(search("courtyard")).options().get(0).roomType().nightlyRateMinor()).isEqualTo(123456);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "980.999", "92233720368547758.08", "1E100"})
    void invalidRatesNeverRoundOrMutate(String value) {
        RoomInventory previous = repository.readSnapshot();
        assertThatThrownBy(() -> management.updateRate("courtyard", new BigDecimal(value)))
                .isInstanceOf(RoomManagementException.class);
        assertThat(repository.readSnapshot()).isSameAs(previous);
    }

    @Test
    void missingRateAndUnknownTypeAreRejected() {
        assertThatThrownBy(() -> management.updateRate("courtyard", null)).isInstanceOf(RoomManagementException.class);
        assertThatThrownBy(() -> management.updateRate("unknown", BigDecimal.ONE)).isInstanceOf(RoomManagementException.class);
    }

    @Test
    void simultaneousDuplicateCreatesHaveExactlyOneWinner() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> create = () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out");
            try {
                management.create("C106", "courtyard", true);
                return true;
            } catch (RoomManagementException exception) {
                if (exception.kind() != RoomManagementException.Kind.CONFLICT) throw exception;
                return false;
            }
        };
        try {
            var first = executor.submit(create);
            var second = executor.submit(create);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(repository.readSnapshot().rooms().stream().filter(room -> room.id().equals("C106"))).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private int available(String type) { return queries.search(search(type)).options().get(0).availableRooms(); }

    private RoomSearchQuery search(String type) {
        LocalDate today = LocalDate.now(clock);
        return new RoomSearchQuery(today.plusDays(10), today.plusDays(12), 1, type);
    }
}
