package com.holidayresort.booking.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class LocalStaffPreviewConfigurationTest {
    @ParameterizedTest
    @CsvSource({"local-staff, 127.0.0.1", "dev;local-staff;mysql, 127.0.0.1", "dev;local-staff;test, 127.0.0.1",
            "dev;local-staff, 0.0.0.0", "dev;local-staff, 192.168.1.1"})
    void previewRefusesNonDevelopmentOrNonLoopbackUse(String profiles, String address) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.split(";"));
        assertThatThrownBy(() -> new LocalStaffPreviewConfiguration(environment, address))
                .isInstanceOf(IllegalStateException.class);
    }
}
