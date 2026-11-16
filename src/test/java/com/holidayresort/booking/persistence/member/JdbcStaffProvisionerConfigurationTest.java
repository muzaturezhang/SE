package com.holidayresort.booking.persistence.member;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.holidayresort.booking.member.application.port.StaffDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class JdbcStaffProvisionerConfigurationTest {

    private final JdbcStaffProvisionerConfiguration configuration =
            new JdbcStaffProvisionerConfiguration();
    private final StaffDirectory directory = mock(StaffDirectory.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void emptyEnvironmentDoesNothing() throws Exception {
        configuration.staffAccountProvisioner(directory, encoder, "", "").run(null);
        verifyNoInteractions(directory, encoder);
    }

    @Test
    void partialOrShortConfigurationFailsClosed() {
        assertThatThrownBy(() -> configuration.staffAccountProvisioner(
                directory, encoder, "ops", "short").run(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validEnvironmentProvisionsBcryptHash() throws Exception {
        when(encoder.encode("long-enough-password")).thenReturn("bcrypt-hash");
        configuration.staffAccountProvisioner(
                directory, encoder, "ops", "long-enough-password").run(null);
        verify(directory).add(new StaffDirectory.StaffRecord("ops", "bcrypt-hash"));
    }

    @Test
    void duplicateProvisioningIsIdempotentAcrossRestart() throws Exception {
        when(encoder.encode(anyString())).thenReturn("bcrypt-hash");
        doThrow(new IllegalStateException("exists"))
                .when(directory).add(org.mockito.ArgumentMatchers.any());
        configuration.staffAccountProvisioner(
                directory, encoder, "ops", "long-enough-password").run(null);
        verify(directory, never()).findByUsername("other");
    }
}
