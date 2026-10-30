package com.holidayresort.booking.room.application;

import java.time.LocalDate;

/** A null roomTypeId means all types. The service validates every query. */
public record RoomSearchQuery(LocalDate arrival, LocalDate departure, int roomCount, String roomTypeId) {
}
