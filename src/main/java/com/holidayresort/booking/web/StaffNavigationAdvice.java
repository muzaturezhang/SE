package com.holidayresort.booking.web;

import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class StaffNavigationAdvice {
    private final Environment environment;

    public StaffNavigationAdvice(Environment environment) { this.environment = environment; }

    @ModelAttribute("localStaffPreview")
    boolean localPreview() { return environment.matchesProfiles("local-staff"); }

    @ModelAttribute("bookingUiPreview")
    boolean bookingPreview() { return environment.matchesProfiles("booking-ui"); }

    @ModelAttribute("accountUiPreview")
    boolean accountPreview() { return environment.matchesProfiles("account-ui"); }

    @ModelAttribute("staffBookingUiPreview")
    boolean staffBookingPreview() { return environment.matchesProfiles("staff-booking-ui"); }

    @ModelAttribute("staffSignedIn")
    boolean staffSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_STAFF"));
    }

    public void addToErrorModel(Model model, Authentication authentication) {
        // Exception-handler models are created without running @ModelAttribute advice.
        model.addAttribute("staffBookingUiPreview", staffBookingPreview());
        model.addAttribute("localStaffPreview", localPreview());
        model.addAttribute("staffSignedIn", staffSignedIn(authentication));
        model.addAttribute("bookingUiPreview", bookingPreview());
        model.addAttribute("accountUiPreview", accountPreview());
    }
}
