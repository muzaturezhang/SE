package com.holidayresort.booking.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;

class AccountUiPreviewConfigurationTest {
    @ParameterizedTest
    @CsvSource({"account-ui, 127.0.0.1", "dev;account-ui;mysql, 127.0.0.1", "dev;account-ui;test, 127.0.0.1",
            "dev;account-ui, 0.0.0.0", "dev;account-ui, 192.168.1.1"})
    void fixturesCannotRunOutsideLoopbackDevelopment(String profiles, String address) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.split(";"));
        assertThatThrownBy(() -> new AccountUiPreviewConfiguration(environment, address)).isInstanceOf(IllegalStateException.class);
    }
}
