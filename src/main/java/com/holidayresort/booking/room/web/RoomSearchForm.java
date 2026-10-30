package com.holidayresort.booking.room.web;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public class RoomSearchForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate arrival;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate departure;

    private Integer rooms = 1;
    private String roomType = "all";

    public LocalDate getArrival() { return arrival; }
    public void setArrival(LocalDate arrival) { this.arrival = arrival; }
    public LocalDate getDeparture() { return departure; }
    public void setDeparture(LocalDate departure) { this.departure = departure; }
    public Integer getRooms() { return rooms; }
    public void setRooms(Integer rooms) { this.rooms = rooms; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
}
