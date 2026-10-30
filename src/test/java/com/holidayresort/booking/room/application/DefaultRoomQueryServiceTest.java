package com.holidayresort.booking.room.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomBlock;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultRoomQueryServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-27T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    private static final RoomType COURTYARD = type("courtyard", 98000);
    private static final RoomType POOL = type("pool", 328001);

    @Test
    void excludesDisabledRoomsAndReturnsExactMinorUnitPrices() {
        var service = service(List.of(
                new Room("C1", "courtyard", true), new Room("C2", "courtyard", false),
                new Room("P1", "pool", true)), List.of());
        var result = service.search(query(1, 3, 1, null));
        assertThat(result.stay().nights()).isEqualTo(2);
        assertThat(result.options()).extracting(RoomAvailability::availableRooms).containsExactly(1, 1);
        assertThat(result.options().get(1).roomType().nightlyRateMinor()).isEqualTo(328001);
    }

    @Test
    void doesNotCombineDifferentRoomsThatAreFreeOnDifferentNights() {
        var service = service(List.of(new Room("C1", "courtyard", true),
                        new Room("C2", "courtyard", true)),
                List.of(block("C1", 1, 2), block("C2", 2, 3)));
        assertThat(service.search(query(1, 2, 1, "courtyard")).options()).hasSize(1);
        assertThat(service.search(query(2, 3, 1, "courtyard")).options()).hasSize(1);
        assertThat(service.search(query(1, 3, 1, "courtyard")).options()).isEmpty();
    }

    @Test
    void permitsArrivalOnAnEarlierGuestsDepartureAndDepartureOnANextGuestsArrival() {
        var service = service(List.of(new Room("C1", "courtyard", true)),
                List.of(block("C1", 0, 2), block("C1", 4, 6)));
        assertThat(service.search(query(2, 4, 1, "courtyard")).options())
                .extracting(RoomAvailability::availableRooms).containsExactly(1);
    }

    @Test
    void countsARoomOnceEvenWhenSeveralBlocksOverlapIt() {
        var service = service(List.of(new Room("C1", "courtyard", true),
                        new Room("C2", "courtyard", true)),
                List.of(block("C1", 1, 4), block("C1", 2, 5)));
        assertThat(service.search(query(2, 3, 1, "courtyard")).options())
                .extracting(RoomAvailability::availableRooms).containsExactly(1);
    }

    @Test
    void filtersTheSelectedTypeAndNeverConsumesRoomsOnRepeatedSearches() {
        var service = service(List.of(new Room("C1", "courtyard", true),
                new Room("P1", "pool", true)), List.of());
        var query = query(0, 1, 1, "pool");
        var first = service.search(query);
        assertThat(first.options()).extracting(option -> option.roomType().id()).containsExactly("pool");
        assertThat(service.search(query)).isEqualTo(first);
    }

    @Test
    void returnsNoResultsWhenTheRequestedQuantityExceedsAvailableRooms() {
        var service = service(List.of(new Room("C1", "courtyard", true)), List.of());
        assertThat(service.search(query(1, 2, Integer.MAX_VALUE, null)).options()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantities(int rooms) {
        assertInvalid(query(1, 2, rooms, null), "rooms");
    }

    @Test
    void rejectsMissingDates() {
        assertInvalid(new RoomSearchQuery(null, TODAY.plusDays(2), 1, null), "arrival");
        assertInvalid(new RoomSearchQuery(TODAY, null, 1, null), "departure");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsSameDayOrReversedDeparture(int departureOffset) {
        assertInvalid(query(0, departureOffset, 1, null), "departure");
    }

    @Test
    void rejectsPastArrivalAndUnknownRoomType() {
        assertInvalid(query(-1, 1, 1, null), "arrival");
        assertInvalid(query(1, 2, 1, "unknown"), "roomType");
    }

    @Test
    void determinesTodayUsingTheInjectedClockZone() {
        Clock afterMidnightInShanghai = Clock.fixed(
                Instant.parse("2026-10-27T16:30:00Z"), ZoneId.of("Asia/Shanghai"));
        var inventory = new RoomInventory(List.of(COURTYARD), List.of(new Room("C1", "courtyard", true)), List.of());
        var service = new DefaultRoomQueryService(() -> inventory, afterMidnightInShanghai);
        assertThatThrownBy(() -> service.search(query(0, 2, 1, null)))
                .isInstanceOf(InvalidRoomSearchException.class);
        assertThat(service.search(query(1, 2, 1, null)).options()).hasSize(1);
    }

    @Test
    void inventoryRejectsDanglingReferencesAndKeepsImmutableCollections() {
        assertThatThrownBy(() -> new RoomInventory(List.of(COURTYARD),
                List.of(new Room("C1", "missing", true)), List.of())).isInstanceOf(IllegalArgumentException.class);
        var inventory = new RoomInventory(List.of(COURTYARD), List.of(), List.of());
        assertThatThrownBy(() -> inventory.roomTypes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertInvalid(RoomSearchQuery query, String field) {
        var service = service(List.of(), List.of());
        assertThatThrownBy(() -> service.search(query))
                .isInstanceOfSatisfying(InvalidRoomSearchException.class,
                        exception -> assertThat(exception.field()).isEqualTo(field));
    }

    private static DefaultRoomQueryService service(List<Room> rooms, List<RoomBlock> blocks) {
        var inventory = new RoomInventory(List.of(COURTYARD, POOL), rooms, blocks);
        return new DefaultRoomQueryService(() -> inventory, CLOCK);
    }

    private static RoomSearchQuery query(int arrival, int departure, int count, String type) {
        return new RoomSearchQuery(TODAY.plusDays(arrival), TODAY.plusDays(departure), count, type);
    }

    private static RoomBlock block(String room, int arrival, int departure) {
        return new RoomBlock(room, new StayPeriod(TODAY.plusDays(arrival), TODAY.plusDays(departure)));
    }

    private static RoomType type(String id, long price) {
        return new RoomType(id, id, id, "description", "/images/suite-interior.jpg", "room", List.of("garden"), price);
    }
}
