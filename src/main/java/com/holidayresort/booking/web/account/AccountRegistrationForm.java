package com.holidayresort.booking.web.account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AccountRegistrationForm extends AccountLoginForm {
    @NotBlank(message = "请输入姓名。")
    @Size(max = 80, message = "姓名最多 80 个字符。")
    private String displayName;
    @NotBlank(message = "请再次输入演示密码。")
    @Size(max = 256, message = "演示输入最多 256 个字符。")
    private String confirmPassword;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    @AssertTrue(message = "两次输入的密码不一致。")
    public boolean isPasswordsMatching() {
        return getPassword() == null || confirmPassword == null || getPassword().equals(confirmPassword);
    }

    @Override
    public void clearSecrets() { super.clearSecrets(); confirmPassword = null; }
}
