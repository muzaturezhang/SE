package com.holidayresort.booking.member.web;

import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.domain.Customer;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** A-owned navigation facts for the shared layout and member error pages. */
@ControllerAdvice
public class MemberNavigationAdvice {

    private final MemberRepository members;

    public MemberNavigationAdvice(MemberRepository members) {
        this.members = members;
    }

    @ModelAttribute("customerSignedIn")
    boolean customerSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
    }

    @ModelAttribute("customerDisplayName")
    String customerDisplayName(Authentication authentication) {
        if (!customerSignedIn(authentication)) {
            return "";
        }
        return members.findById(authentication.getName()).map(Customer::displayName).orElse("");
    }

    public void addToErrorModel(Model model, Authentication authentication) {
        model.addAttribute("customerSignedIn", customerSignedIn(authentication));
        model.addAttribute("customerDisplayName", customerDisplayName(authentication));
    }
}
