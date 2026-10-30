package com.holidayresort.booking.room.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("local-staff")
public class LocalStaffPreviewController {
    @GetMapping("/staff-preview/login")
    String login() { return "room/admin/login"; }
}
