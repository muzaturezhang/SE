package com.holidayresort.booking.room.application;

import com.holidayresort.booking.room.application.port.RoomRepository;
import com.holidayresort.booking.room.domain.RoomInventory;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.room.domain.StayPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DefaultRoomQueryService implements RoomQueryService {

    private final RoomRepository repository;
    private final Clock clock;

    public DefaultRoomQueryService(RoomRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public List<RoomType> listRoomTypes() {
        return repository.readSnapshot().roomTypes();
    }

    @Override
    public RoomSearchResult search(RoomSearchQuery query) {
        validate(query);
        RoomInventory inventory = repository.readSnapshot();
        if (query.roomTypeId() != null && inventory.roomTypes().stream()
                .noneMatch(type -> type.id().equals(query.roomTypeId()))) {
            throw new InvalidRoomSearchException("roomType", "找不到所选房型，请重新选择。");
        }
        StayPeriod stay = new StayPeriod(query.arrival(), query.departure());
        Set<String> blockedRoomIds = new HashSet<>();
        inventory.blocks().stream()
                .filter(block -> block.period().overlaps(stay))
                .forEach(block -> blockedRoomIds.add(block.roomId()));
        List<RoomAvailability> options = inventory.roomTypes().stream()
                .filter(type -> query.roomTypeId() == null || type.id().equals(query.roomTypeId()))
                .map(type -> new RoomAvailability(type, Math.toIntExact(inventory.rooms().stream()
                        .filter(room -> room.enabled() && room.roomTypeId().equals(type.id()))
                        .filter(room -> !blockedRoomIds.contains(room.id()))
                        .count())))
                .filter(option -> option.availableRooms() >= query.roomCount())
                .toList();
        return new RoomSearchResult(stay, query.roomCount(), options);
    }

    private void validate(RoomSearchQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Query is required");
        }
        if (query.arrival() == null) {
            throw new InvalidRoomSearchException("arrival", "请选择入住日期。");
        }
        if (query.departure() == null) {
            throw new InvalidRoomSearchException("departure", "请选择退房日期。");
        }
        if (query.arrival().isBefore(LocalDate.now(clock))) {
            throw new InvalidRoomSearchException("arrival", "入住日期不能早于今天。");
        }
        if (!query.departure().isAfter(query.arrival())) {
            throw new InvalidRoomSearchException("departure", "退房日期必须晚于入住日期，至少入住一晚。");
        }
        if (query.roomCount() < 1) {
            throw new InvalidRoomSearchException("rooms", "房间数量必须为正整数。");
        }
    }
}
