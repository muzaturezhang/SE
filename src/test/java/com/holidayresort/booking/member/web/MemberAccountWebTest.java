package com.holidayresort.booking.member.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.holidayresort.booking.member.application.port.StaffDirectory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:member-account-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MemberAccountWebTest {

    @Autowired MockMvc mvc;
    @Autowired StaffDirectory staffDirectory;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void loginPageIsPublicAndAccountPagesRequireAuthentication() throws Exception {
        mvc.perform(get("/account/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("账户登录")));
        mvc.perform(get("/account/register")).andExpect(status().isOk());
        mvc.perform(get("/account")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/account/login"));
        mvc.perform(get("/account/membership")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/rooms")).andExpect(status().isForbidden());
    }

    @Test
    void registrationThenLoginGrantsCustomerSessionWithOwnProfile() throws Exception {
        mvc.perform(register(Map.of())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/login"));
        var result = mvc.perform(post("/account/login").with(csrf())
                        .param("username", "lin.xiao@example.com").param("password", "safePass1"))
                .andExpect(authenticated().withRoles("CUSTOMER"))
                .andExpect(redirectedUrl("/account")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/account").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("林晓")))
                .andExpect(content().string(containsString("lin.xiao@example.com")))
                .andExpect(content().string(containsString("未开通")))
                .andExpect(content().string(not(containsString("safePass1"))));
        mvc.perform(get("/admin/rooms").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(unauthenticated())
                .andExpect(redirectedUrl("/rooms"));
        mvc.perform(get("/account").session(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void duplicateWeakAndInvalidRegistrationAreReadableAndNeverEchoPasswords() throws Exception {
        mvc.perform(register(Map.of("email", "dup@example.invalid", "password", "dupPass1", "confirmPassword", "dupPass1")))
                .andExpect(status().is3xxRedirection());
        mvc.perform(register(Map.of("email", "DUP@example.invalid", "password", "dupPass1", "confirmPassword", "dupPass1")))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("该邮箱已注册")))
                .andExpect(content().string(not(containsString("dupPass1"))));
        mvc.perform(register(Map.of("email", "weak@example.invalid", "password", "abcdefgh", "confirmPassword", "abcdefgh")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("同时包含字母和数字")));
        mvc.perform(register(Map.of("email", "mismatch@example.invalid", "password", "rightPass1", "confirmPassword", "wrongPass2")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("两次输入的密码不一致")));
    }

    @Test
    void membershipJoinRecordsPendingApplicationWithoutPayment() throws Exception {
        var session = customerSession("join@example.invalid");
        mvc.perform(get("/account/membership").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("未开通")))
                .andExpect(content().string(containsString("CNY 680.00")))
                .andExpect(content().string(containsString("95 折")));
        mvc.perform(post("/account/membership/join").session(session).with(csrf()).param("intent", "join"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account/membership"));
        mvc.perform(get("/account/membership").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("待确认")))
                .andExpect(content().string(containsString("等待支付服务确认")));
        mvc.perform(get("/account/membership/join").session(session).param("intent", "unknown"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/account/membership/join").session(session).param("intent", "join"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordFailsReadablyAndLoginAndRegistrationRequireCsrf() throws Exception {
        mvc.perform(register(Map.of("email", "wrong@example.invalid")))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/account/login").with(csrf())
                        .param("username", "wrong@example.invalid").param("password", "badPass1"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/account/login?error"));
        mvc.perform(get("/account/login").param("error", "")).andExpect(status().isOk())
                .andExpect(content().string(containsString("邮箱或密码不正确")));
        mvc.perform(post("/account/register")).andExpect(status().isForbidden());
        mvc.perform(post("/account/login").param("username", "wrong@example.invalid").param("password", "badPass1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void provisionedStaffCanSignInThroughTheSharedLoginAndReachAdmin() throws Exception {
        staffDirectory.add(new StaffDirectory.StaffRecord("frontdesk", passwordEncoder.encode("staffPass1")));
        var result = mvc.perform(post("/account/login").with(csrf())
                        .param("username", "frontdesk").param("password", "staffPass1"))
                .andExpect(authenticated().withRoles("STAFF"))
                .andExpect(redirectedUrl("/admin/rooms")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/admin/rooms").session(session)).andExpect(status().isOk());
        mvc.perform(get("/account").session(session)).andExpect(status().isForbidden());
    }

    private MockHttpSession customerSession(String email) throws Exception {
        mvc.perform(register(Map.of("email", email, "password", "safePass1", "confirmPassword", "safePass1")))
                .andExpect(status().is3xxRedirection());
        var result = mvc.perform(post("/account/login").with(csrf())
                        .param("username", email).param("password", "safePass1"))
                .andExpect(authenticated()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder register(Map<String, String> overrides) {
        var fields = new HashMap<>(Map.of("displayName", "林晓", "email", "lin.xiao@example.com",
                "password", "safePass1", "confirmPassword", "safePass1"));
        fields.putAll(overrides);
        var request = post("/account/register").with(csrf());
        fields.forEach(request::param);
        return request;
    }
}
