package com.holidayresort.booking.member.application;

import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.application.port.StaffDirectory;
import java.util.Locale;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Single identity lookup for both provisioned STAFF accounts and registered
 * CUSTOMER accounts. Customer principals carry the internal customer id so
 * other modules never accept a request-supplied identity.
 */
@Service
public class ResortUserDetailsService implements UserDetailsService {

    private final StaffDirectory staffDirectory;
    private final MemberRepository members;

    public ResortUserDetailsService(StaffDirectory staffDirectory, MemberRepository members) {
        this.staffDirectory = staffDirectory;
        this.members = members;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        String supplied = username.strip();
        return staffDirectory.findByUsername(supplied)
                .map(staff -> User.withUsername(staff.username())
                        .password(staff.passwordHash()).roles("STAFF").build())
                .or(() -> members.findByEmail(supplied.toLowerCase(Locale.ROOT))
                        .map(customer -> User.withUsername(customer.id())
                                .password(customer.passwordHash()).roles("CUSTOMER").build()))
                .orElseThrow(() -> new UsernameNotFoundException("账号或密码不正确"));
    }
}
