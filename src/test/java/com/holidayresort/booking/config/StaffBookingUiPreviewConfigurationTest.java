package com.holidayresort.booking.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class StaffBookingUiPreviewConfigurationTest {
    @ParameterizedTest
    @CsvSource({"staff-booking-ui,127.0.0.1", "dev;staff-booking-ui;mysql,127.0.0.1", "dev;staff-booking-ui;test,127.0.0.1",
            "dev;staff-booking-ui,0.0.0.0", "dev;staff-booking-ui,192.168.1.1"})
    void unsafeConfigurationIsRejected(String profiles, String address) {
        MockEnvironment environment = new MockEnvironment(); environment.setActiveProfiles(profiles.split(";"));
        assertThatThrownBy(() -> new StaffBookingUiPreviewConfiguration(environment, address)).isInstanceOf(IllegalStateException.class);
    }
    @Test void loopbackDevelopmentIsAllowedWithoutRequiringLocalStaffAuthImplementation() {
        MockEnvironment environment = new MockEnvironment(); environment.setActiveProfiles("dev", "staff-booking-ui");
        assertThatCode(() -> new StaffBookingUiPreviewConfiguration(environment, "127.0.0.1")).doesNotThrowAnyException();
        assertThatCode(() -> new StaffBookingUiPreviewConfiguration(environment, "::1")).doesNotThrowAnyException();
    }
}
