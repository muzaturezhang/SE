package com.holidayresort.booking.room.application;

import static com.holidayresort.booking.room.application.RoomManagementException.Kind.*;

import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import com.holidayresort.booking.room.domain.Room;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** B's management rules. Authentication is enforced by the STAFF HTTP boundary. */
@Service
public class RoomManagementService {
    private final RoomInventoryEditor repository;
    private final Clock clock;

    public RoomManagementService(RoomInventoryEditor repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<ManagedRoom> list(String typeId, String state) {
        RoomInventory inventory = repository.readSnapshot();
        if (typeId != null && !typeId.isBlank()) requireType(inventory, typeId);
        if (state == null || !List.of("all", "enabled", "disabled").contains(state)) {
            throw error(INVALID, "state", "请选择有效的房间状态。");
        }
        LocalDate today = LocalDate.now(clock);
        return inventory.rooms().stream()
                .filter(room -> typeId == null || typeId.isBlank() || room.roomTypeId().equals(typeId))
                .filter(room -> state.equals("all") || room.enabled() == state.equals("enabled"))
                .sorted(Comparator.comparing(Room::id))
                .map(room -> new ManagedRoom(room, requireType(inventory, room.roomTypeId()),
                        blocked(inventory, room.id(), today)))
                .toList();
    }

    public ManagedRoom get(String roomId) {
        RoomInventory inventory = repository.readSnapshot();
        Room room = requireRoom(inventory, roomId);
        return new ManagedRoom(room, requireType(inventory, room.roomTypeId()),
                blocked(inventory, roomId, LocalDate.now(clock)));
    }

    public void create(String roomId, String typeId, boolean enabled) {
        String id = roomId == null ? "" : roomId.strip();
        if (!id.matches("[A-Z0-9][A-Z0-9-]{0,19}")) {
            throw error(INVALID, "roomId", "房号须为 1–20 位大写字母、数字或短横线，且以字母或数字开头。");
        }
        repository.updateSnapshot(inventory -> {
            requireType(inventory, typeId);
            if (inventory.rooms().stream().anyMatch(room -> room.id().equals(id))) {
                throw error(CONFLICT, "roomId", "这个房号已存在，请使用其他房号。");
            }
            List<Room> rooms = new ArrayList<>(inventory.rooms());
            rooms.add(new Room(id, typeId, enabled));
            return new RoomInventory(inventory.roomTypes(), rooms, inventory.blocks());
        });
    }

    public void update(String roomId, String typeId, boolean enabled) {
        repository.updateSnapshot(inventory -> {
            Room previous = requireRoom(inventory, roomId);
            requireType(inventory, typeId);
            boolean changingType = !previous.roomTypeId().equals(typeId);
            if ((!enabled || changingType) && blocked(inventory, roomId, LocalDate.now(clock))) {
                throw error(CONFLICT, "roomTypeId", "该房间存在当前或未来占用，不能停用或更换房型。");
            }
            return replace(inventory, new Room(roomId, typeId, enabled));
        });
    }

    public void disable(String roomId) {
        repository.updateSnapshot(inventory -> {
            Room previous = requireRoom(inventory, roomId);
            if (blocked(inventory, roomId, LocalDate.now(clock))) {
                throw error(CONFLICT, "roomId", "该房间存在当前或未来占用，暂时不能停用。");
            }
            return replace(inventory, new Room(roomId, previous.roomTypeId(), false));
        });
    }

    public void updateRate(String typeId, BigDecimal nightlyRate) {
        final long minor;
        try {
            if (nightlyRate == null || nightlyRate.signum() <= 0) throw new ArithmeticException();
            minor = nightlyRate.movePointRight(2).longValueExact();
        } catch (ArithmeticException exception) {
            throw error(INVALID, "nightlyRate", "每晚房价须为大于零、最多两位小数且在支持范围内的 CNY 金额。");
        }
        repository.updateSnapshot(inventory -> {
            RoomType previous = requireType(inventory, typeId);
            RoomType replacement = new RoomType(previous.id(), previous.name(), previous.englishName(),
                    previous.description(), previous.imagePath(), previous.imageAlt(), previous.features(), minor);
            return new RoomInventory(inventory.roomTypes().stream()
                    .map(type -> type.id().equals(typeId) ? replacement : type).toList(),
                    inventory.rooms(), inventory.blocks());
        });
    }

    private RoomInventory replace(RoomInventory inventory, Room replacement) {
        return new RoomInventory(inventory.roomTypes(), inventory.rooms().stream()
                .map(room -> room.id().equals(replacement.id()) ? replacement : room).toList(), inventory.blocks());
    }

    private boolean blocked(RoomInventory inventory, String roomId, LocalDate today) {
        return inventory.blocks().stream().anyMatch(block -> block.roomId().equals(roomId)
                && block.period().departure().isAfter(today));
    }

    private Room requireRoom(RoomInventory inventory, String roomId) {
        return inventory.rooms().stream().filter(room -> room.id().equals(roomId)).findFirst()
                .orElseThrow(() -> error(NOT_FOUND, "roomId", "找不到这个房间，请返回列表重新选择。"));
    }

    private RoomType requireType(RoomInventory inventory, String typeId) {
        return inventory.roomTypes().stream().filter(type -> type.id().equals(typeId)).findFirst()
                .orElseThrow(() -> error(INVALID, "roomTypeId", "请选择已有的房型。"));
    }

    private RoomManagementException error(RoomManagementException.Kind kind, String field, String message) {
        return new RoomManagementException(kind, field, message);
    }
}
