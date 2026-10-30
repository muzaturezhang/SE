package com.holidayresort.booking.room.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomBlock;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;

/** Development fixture adapter; deliberately unavailable with the mysql profile. */
@Repository
@Profile("!mysql & (dev | test)")
public class InMemoryRoomRepository implements RoomInventoryEditor {

    private volatile RoomInventory inventory;

    public InMemoryRoomRepository(
            @Value("${room.sample-data-location:classpath:fixtures/room-inventory.json}") Resource resource,
            ObjectMapper mapper, Clock clock) {
        try (InputStream input = resource.getInputStream()) {
            SeedData seed = mapper.readValue(input, SeedData.class);
            LocalDate base = LocalDate.now(clock);
            List<RoomBlock> blocks = seed.blocks().stream()
                    .map(block -> new RoomBlock(block.roomId(), new StayPeriod(
                            base.plusDays(block.arrivalOffsetDays()),
                            base.plusDays(block.departureOffsetDays()))))
                    .toList();
            inventory = new RoomInventory(seed.roomTypes(), seed.rooms(), blocks);
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot load room sample inventory", exception);
        }
    }

    @Override
    public RoomInventory readSnapshot() {
        return inventory;
    }

    @Override
    public synchronized void updateSnapshot(UnaryOperator<RoomInventory> change) {
        // A failed validation leaves the previous immutable snapshot intact.
        inventory = Objects.requireNonNull(change.apply(inventory));
    }

    private record SeedData(List<RoomType> roomTypes, List<Room> rooms, List<BlockSeed> blocks) {
    }

    private record BlockSeed(String roomId, int arrivalOffsetDays, int departureOffsetDays) {
    }
}
