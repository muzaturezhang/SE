package com.holidayresort.booking.booking.infrastructure;

import com.holidayresort.booking.booking.application.BookingException;
import com.holidayresort.booking.booking.application.port.BookingInventoryPort;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomBlock;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Atomic in-process implementation used by dev and test profiles. */
@Component
@Profile("!mysql & (dev | test)")
public class InMemoryBookingInventoryPort implements BookingInventoryPort {

    private final RoomInventoryEditor inventory;

    public InMemoryBookingInventoryPort(RoomInventoryEditor inventory) {
        this.inventory = inventory;
    }

    @Override
    public synchronized Booking allocate(
            String roomTypeId, StayPeriod stay, int roomCount, AllocationFactory factory) {
        AtomicReference<Booking> saved = new AtomicReference<>();
        inventory.updateSnapshot(current -> {
            RoomType type = current.roomTypes().stream()
                    .filter(candidate -> candidate.id().equals(roomTypeId))
                    .findFirst()
                    .orElseThrow(() -> new BookingException(
                            BookingException.Kind.INVALID, "roomType", "Unknown room type."));
            List<String> available = new ArrayList<>();
            for (Room room : current.rooms()) {
                if (!room.roomTypeId().equals(roomTypeId) || !room.enabled()) {
                    continue;
                }
                boolean blocked = current.blocks().stream()
                        .anyMatch(block -> block.roomId().equals(room.id())
                                && block.period().overlaps(stay));
                if (!blocked) {
                    available.add(room.id());
                }
            }
            if (available.size() < roomCount) {
                throw new BookingException(BookingException.Kind.CONFLICT, "rooms",
                        "Not enough rooms available for the selected dates. Please adjust your stay.");
            }
            List<String> picked = List.copyOf(available.subList(0, roomCount));
            Booking booking = factory.create(type, picked);
            saved.set(booking);
            List<RoomBlock> blocks = new ArrayList<>(current.blocks());
            picked.forEach(roomId -> blocks.add(new RoomBlock(roomId, stay)));
            return new RoomInventory(current.roomTypes(), current.rooms(), blocks);
        });
        return saved.get();
    }

    @Override
    public synchronized Booking change(Booking current, String roomTypeId, StayPeriod stay,
            int roomCount, AllocationFactory factory) {
        AtomicReference<Booking> saved = new AtomicReference<>();
        inventory.updateSnapshot(snapshot -> {
            List<RoomBlock> baseBlocks = snapshot.blocks().stream()
                    .filter(block -> !(current.allocatedRoomIds().contains(block.roomId())
                            && block.period().equals(current.stay())))
                    .toList();
            RoomType type = snapshot.roomTypes().stream()
                    .filter(candidate -> candidate.id().equals(roomTypeId))
                    .findFirst()
                    .orElseThrow(() -> new BookingException(
                            BookingException.Kind.INVALID, "roomType", "Unknown room type."));
            List<String> available = new ArrayList<>();
            for (Room room : snapshot.rooms()) {
                if (!room.roomTypeId().equals(roomTypeId) || !room.enabled()) {
                    continue;
                }
                boolean blocked = baseBlocks.stream().anyMatch(block -> block.roomId().equals(room.id())
                        && block.period().overlaps(stay));
                if (!blocked) {
                    available.add(room.id());
                }
            }
            if (available.size() < roomCount) {
                throw new BookingException(BookingException.Kind.CONFLICT, "rooms",
                        "Not enough rooms available for the changed stay.");
            }
            List<String> picked = List.copyOf(available.subList(0, roomCount));
            Booking changed = factory.create(type, picked);
            saved.set(changed);
            List<RoomBlock> blocks = new ArrayList<>(baseBlocks);
            picked.forEach(roomId -> blocks.add(new RoomBlock(roomId, stay)));
            return new RoomInventory(snapshot.roomTypes(), snapshot.rooms(), blocks);
        });
        return saved.get();
    }
    public synchronized void release(Booking booking, String reason) {
        inventory.updateSnapshot(current -> {
            List<RoomBlock> kept = current.blocks().stream()
                    .filter(block -> !(booking.allocatedRoomIds().contains(block.roomId())
                            && block.period().equals(booking.stay())))
                    .toList();
            return new RoomInventory(current.roomTypes(), current.rooms(), kept);
        });
    }
}
