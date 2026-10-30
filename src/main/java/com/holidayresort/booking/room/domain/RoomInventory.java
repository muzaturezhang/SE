package com.holidayresort.booking.room.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** An immutable, internally consistent snapshot for one query. */
public record RoomInventory(List<RoomType> roomTypes, List<Room> rooms, List<RoomBlock> blocks) {

    public RoomInventory {
        roomTypes = List.copyOf(roomTypes);
        rooms = List.copyOf(rooms);
        blocks = List.copyOf(blocks);
        Set<String> typeIds = new HashSet<>();
        for (RoomType type : roomTypes) {
            if (!typeIds.add(type.id())) {
                throw new IllegalArgumentException("Duplicate room type identifier: " + type.id());
            }
        }
        Set<String> roomIds = new HashSet<>();
        for (Room room : rooms) {
            if (!roomIds.add(room.id()) || !typeIds.contains(room.roomTypeId())) {
                throw new IllegalArgumentException("Duplicate room or unknown room type: " + room.id());
            }
        }
        for (RoomBlock block : blocks) {
            if (!roomIds.contains(block.roomId())) {
                throw new IllegalArgumentException("Block refers to unknown room: " + block.roomId());
            }
        }
    }
}
