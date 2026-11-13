package com.holidayresort.booking.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.holidayresort.booking.member.application.MemberAccountException.Kind;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.member.infrastructure.InMemoryMemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RegistrationServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private InMemoryMemberRepository repository;
    private BCryptPasswordEncoder encoder;
    private RegistrationService service;

    @BeforeEach
    void setup() {
        repository = new InMemoryMemberRepository();
        encoder = new BCryptPasswordEncoder();
        service = new RegistrationService(repository, encoder, clock);
    }

    @Test
    void registersNormalizedCustomerWithHashedPassword() {
        Customer customer = service.register("  林晓 ", " LIN.XIAO@Example.com ", "safePass1");
        assertThat(customer.displayName()).isEqualTo("林晓");
        assertThat(customer.email()).isEqualTo("lin.xiao@example.com");
        assertThat(customer.passwordHash()).isNotEqualTo("safePass1");
        assertThat(encoder.matches("safePass1", customer.passwordHash())).isTrue();
        assertThat(customer.registeredOn()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(repository.findById(customer.id())).contains(customer);
        assertThat(repository.findByEmail("lin.xiao@example.com")).contains(customer);
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitivelyWithoutChangingStore() {
        service.register("林晓", "lin.xiao@example.com", "safePass1");
        assertThatThrownBy(() -> service.register("另一人", "LIN.XIAO@example.com", "otherPass2"))
                .isInstanceOfSatisfying(MemberAccountException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.CONFLICT);
                    assertThat(exception.field()).isEqualTo("email");
                });
        assertThat(repository.findByEmail("lin.xiao@example.com").orElseThrow().displayName()).isEqualTo("林晓");
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "abcdefgh", "12345678", "password", "  spaced1  "})
    void weakOrMisplacedPasswordsAreRejected(String password) {
        assertThatThrownBy(() -> service.register("林晓", "guest@example.invalid", password))
                .isInstanceOfSatisfying(MemberAccountException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.INVALID);
                    assertThat(exception.field()).isEqualTo("password");
                });
    }

    @Test
    void passwordsLongerThanSeventyTwoCharactersAreRejected() {
        String longPassword = "a".repeat(73) + "1";
        assertThatThrownBy(() -> service.register("林晓", "guest@example.invalid", longPassword))
                .isInstanceOfSatisfying(MemberAccountException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.INVALID);
                    assertThat(exception.field()).isEqualTo("password");
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-an-email", "a@b", "no-dot@x"})
    void invalidEmailsAreRejected(String email) {
        assertThatThrownBy(() -> service.register("林晓", email, "safePass1"))
                .isInstanceOfSatisfying(MemberAccountException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.INVALID);
                    assertThat(exception.field()).isEqualTo("email");
                });
    }

    @Test
    void oversizedEmailAndNameAreRejected() {
        String oversizedEmail = "x".repeat(300) + "@example.invalid";
        assertThatThrownBy(() -> service.register("林晓", oversizedEmail, "safePass1"))
                .isInstanceOfSatisfying(MemberAccountException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.INVALID);
                    assertThat(exception.field()).isEqualTo("email");
                });
        String oversizedName = "x".repeat(81);
        assertThatThrownBy(() -> service.register(oversizedName, "guest@example.invalid", "safePass1"))
                .isInstanceOfSatisfying(MemberAccountException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.INVALID);
                    assertThat(exception.field()).isEqualTo("displayName");
                });
    }

    @ParameterizedTest
    @NullAndEmptySource
    void blankNamesAreRejected(String name) {
        assertThatThrownBy(() -> service.register(name, "guest@example.invalid", "safePass1"))
                .isInstanceOfSatisfying(MemberAccountException.class, exception -> {
                    assertThat(exception.kind()).isEqualTo(Kind.INVALID);
                    assertThat(exception.field()).isEqualTo("displayName");
                });
    }
}
