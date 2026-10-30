package com.holidayresort.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/** Disposable, opt-in local UI verification only. A supplies the real authentication module. */
@Configuration
@Profile("local-staff")
public class LocalStaffPreviewConfiguration {
    public LocalStaffPreviewConfiguration(Environment environment,
            @Value("${server.address}") String address) {
        if (!environment.matchesProfiles("dev") || environment.matchesProfiles("mysql", "test")
                || !(address.equals("127.0.0.1") || address.equals("::1"))) {
            throw new IllegalStateException("local-staff requires dev, loopback binding, and no mysql/test profile");
        }
    }

    @Bean
    UserDetailsService localStaffPreviewUsers(@Value("${LOCAL_STAFF_USERNAME}") String username,
            @Value("${LOCAL_STAFF_PASSWORD}") String password) {
        if (username.isBlank() || password.length() < 16) {
            throw new IllegalStateException("Local preview requires a username and a password of at least 16 characters");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("STAFF").build());
    }
}
