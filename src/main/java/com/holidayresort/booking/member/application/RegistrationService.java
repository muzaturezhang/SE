package com.holidayresort.booking.member.application;

import com.holidayresort.booking.member.application.MemberAccountException.Kind;
import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.domain.Customer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * A-owned customer registration. Passwords follow the team policy and are
 * stored only as BCrypt hashes.
 */
@Service
public class RegistrationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern LETTER_PATTERN = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");

    private final MemberRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(MemberRepository repository, PasswordEncoder passwordEncoder, Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    public Customer register(String displayName, String email, String password) {
        String name = displayName == null ? "" : displayName.strip();
        String address = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.length() > 80) {
            throw invalid("displayName", "请输入姓名，最多 80 个字符。");
        }
        if (address.length() > 254 || !EMAIL_PATTERN.matcher(address).matches()) {
            throw invalid("email", "请输入有效的邮箱地址。");
        }
        validatePassword(password);
        if (repository.findByEmail(address).isPresent()) {
            throw conflict("email", "该邮箱已注册，请直接登录。");
        }
        Customer customer = new Customer(UUID.randomUUID().toString(), name, address,
                passwordEncoder.encode(password), LocalDate.now(clock));
        return repository.save(customer);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw invalid("password", "密码需为 8–72 个字符。");
        }
        if (!password.equals(password.strip())) {
            throw invalid("password", "密码不能以空格开头或结尾。");
        }
        if (!LETTER_PATTERN.matcher(password).find() || !DIGIT_PATTERN.matcher(password).find()) {
            throw invalid("password", "密码需同时包含字母和数字。");
        }
    }

    private static MemberAccountException invalid(String field, String message) {
        return new MemberAccountException(Kind.INVALID, field, message);
    }

    private static MemberAccountException conflict(String field, String message) {
        return new MemberAccountException(Kind.CONFLICT, field, message);
    }
}
