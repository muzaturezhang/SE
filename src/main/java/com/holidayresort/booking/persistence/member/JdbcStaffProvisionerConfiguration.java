package com.holidayresort.booking.persistence.member;

import com.holidayresort.booking.member.application.port.StaffDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Optional environment-driven provisioning for a production STAFF account. */
@Configuration
@Profile("mysql")
public class JdbcStaffProvisionerConfiguration {

    @Bean
    ApplicationRunner staffAccountProvisioner(
            StaffDirectory staffDirectory,
            PasswordEncoder passwordEncoder,
            @Value("${STAFF_USERNAME:}") String username,
            @Value("${STAFF_PASSWORD:}") String password) {
        return args -> {
            if (username.isBlank() && password.isBlank()) {
                return;
            }
            if (username.isBlank() || password.length() < 16) {
                throw new IllegalStateException(
                        "STAFF_USERNAME and a STAFF_PASSWORD of at least 16 characters are required together");
            }
            try {
                staffDirectory.add(new StaffDirectory.StaffRecord(
                        username.strip(), passwordEncoder.encode(password)));
            } catch (IllegalStateException alreadyProvisioned) {
                // Provisioning is intentionally idempotent across restarts.
            }
        };
    }
}
