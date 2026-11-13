package com.holidayresort.booking.member.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request-only transport for real registration. Passwords are never re-rendered. */
public class MemberRegistrationForm {

    @NotBlank(message = "请输入姓名。")
    @Size(max = 80, message = "姓名最多 80 个字符。")
    private String displayName;

    @NotBlank(message = "请输入邮箱。")
    @Email(message = "请输入有效的邮箱。")
    @Size(max = 254, message = "邮箱最多 254 个字符。")
    private String email;

    @NotBlank(message = "请输入密码。")
    @Size(min = 8, max = 72, message = "密码需为 8–72 个字符。")
    private String password;

    @NotBlank(message = "请再次输入密码。")
    @Size(min = 8, max = 72, message = "密码需为 8–72 个字符。")
    private String confirmPassword;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }

    @AssertTrue(message = "两次输入的密码不一致。")
    public boolean isPasswordsMatching() {
        return password == null || confirmPassword == null || password.equals(confirmPassword);
    }

    public void clearSecrets() {
        password = null;
        confirmPassword = null;
    }
}
