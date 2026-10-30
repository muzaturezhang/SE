package com.holidayresort.booking.room.application;

import com.holidayresort.booking.room.domain.RoomType;
import java.util.List;

/** Query-only draft for B/C review. Search never reserves inventory. */
public interface RoomQueryService {

    List<RoomType> listRoomTypes();

    RoomSearchResult search(RoomSearchQuery query);
}
