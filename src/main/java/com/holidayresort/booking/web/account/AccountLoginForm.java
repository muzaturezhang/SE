package com.holidayresort.booking.web.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request-only transport. Never put this form in a Session, flash attribute or log. */
public class AccountLoginForm {
    @NotBlank(message = "请输入邮箱。")
    @Email(message = "请输入有效的邮箱。")
    @Size(max = 254, message = "邮箱最多 254 个字符。")
    private String email;
    @NotBlank(message = "请输入演示密码。")
    @Size(max = 256, message = "演示输入最多 256 个字符，正式密码规则待账号服务确认。")
    private String password;
    private String next;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNext() { return next; }
    public void setNext(String next) { this.next = next; }
    public void clearSecrets() { password = null; }
}
