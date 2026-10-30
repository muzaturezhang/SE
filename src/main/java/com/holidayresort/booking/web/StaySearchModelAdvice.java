package com.holidayresort.booking.web;

import com.holidayresort.booking.room.web.RoomPageController;
import com.holidayresort.booking.room.web.RoomSearchForm;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {HomeController.class, RoomPageController.class})
public class StaySearchModelAdvice {

    private final Clock clock;

    public StaySearchModelAdvice(Clock clock) {
        this.clock = clock;
    }

    @ModelAttribute("staySearch")
    RoomSearchForm staySearch() { return new RoomSearchForm(); }

    @ModelAttribute("today")
    LocalDate today() { return LocalDate.now(clock); }

    @ModelAttribute("minimumDeparture")
    LocalDate minimumDeparture() { return LocalDate.now(clock).plusDays(1); }
}
