package com.holidayresort.booking.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class BookingUiPreviewConfigurationTest {
    @ParameterizedTest
    @CsvSource({"booking-ui, 127.0.0.1", "dev;booking-ui;mysql, 127.0.0.1", "dev;booking-ui;test, 127.0.0.1",
            "dev;booking-ui, 0.0.0.0", "dev;booking-ui, 192.168.1.1"})
    void publicFixturesAreRestrictedToLocalDevelopment(String profiles, String address) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.split(";"));
        assertThatThrownBy(() -> new BookingUiPreviewConfiguration(environment, address)).isInstanceOf(IllegalStateException.class);
    }
}
