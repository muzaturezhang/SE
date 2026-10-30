package com.holidayresort.booking.web.account;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:account-ui-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "account-ui", "booking-ui"})
class AccountPreviewWebTest {
    @Autowired MockMvc mvc;

    @Test
    void guestProfileRedirectsToLoginWithoutLeakingModelAttributes() throws Exception {
        mvc.perform(get("/preview/account")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/preview/account/login?next=/preview/account"));
        mvc.perform(get("/preview/account/login")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/preview/account/register")).andExpect(status().isOk());
    }

    @Test
    void loginPresentationReturnsToExactSelectionWithoutAuthenticatingOrGrantingRoles() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String target = "/preview/stays/new?roomType=courtyard&arrival=2026-11-07&departure=2026-11-09&rooms=2";
        mvc.perform(login(session, Map.of("next", target)).param("roles", "STAFF").param("membershipState", "ACTIVE"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(target)).andExpect(unauthenticated());
        mvc.perform(get("/preview/account").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("guest@example.invalid")))
                .andExpect(content().string(containsString("非真实身份"))).andExpect(unauthenticated());
        mvc.perform(get("/admin/rooms").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/preview/account").session(new MockHttpSession())).andExpect(status().is3xxRedirection());
    }

    @Test
    void registrationProducesOnlyContactPreviewAndPreservesNoPassword() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var result = mvc.perform(register(session, Map.of("email", "preview@example.invalid"))).andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated()).andReturn();
        String location = result.getResponse().getRedirectedUrl();
        mvc.perform(get(location).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("注册表单校验通过")))
                .andExpect(content().string(not(containsString("UI-ONLY-SECRET"))))
                .andExpect(content().string(containsString("演示住客")));
        mvc.perform(get("/preview/account").session(session)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/preview/account/login").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("preview@example.invalid")));
        mvc.perform(get("/preview/account/registration-result").session(new MockHttpSession())).andExpect(status().isNotFound());
    }

    @Test
    void invalidFieldsAndMismatchedPasswordsAreReadableAndNeverEchoPasswords() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var result = mvc.perform(register(session, Map.of("email", "bad-email", "confirmPassword", "DIFFERENT-SECRET")))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("两次输入的密码不一致")))
                .andExpect(content().string(containsString("有效的邮箱")))
                .andExpect(content().string(not(containsString("UI-ONLY-SECRET"))))
                .andExpect(content().string(not(containsString("DIFFERENT-SECRET")))).andReturn();
        var form = (AccountRegistrationForm) result.getModelAndView().getModel().get("registerForm");
        assertThat(form.getPassword()).isNull();
        assertThat(form.getConfirmPassword()).isNull();
        mvc.perform(post("/preview/account/login").session(session).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("请输入演示密码")));
        mvc.perform(post("/preview/account/register").session(session).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(get("/preview/account/registration-result").session(session)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource({"displayName, 81", "email, 255", "password, 257", "confirmPassword, 257"})
    void serverEnforcesTransportBoundsWithoutStoringOversizedValues(String field, int size) throws Exception {
        String value = field.equals("email") ? "x".repeat(size) + "@example.invalid" : "x".repeat(size);
        mvc.perform(register(new MockHttpSession(), Map.of(field, value)))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("最多")))
                .andExpect(content().string(not(containsString("UI-ONLY-SECRET"))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.invalid", "//example.invalid", "/admin/rooms", "/preview/account#fragment",
            "/preview/stays/new?next=https://example.invalid", "/preview/stays/new?rooms=1&rooms=2", "/%2fexample.invalid"})
    void invalidReturnTargetIsRejectedOnGetAndPostWithoutChangingPresentation(String target) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/preview/account/login").session(session).param("next", target)).andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("返回位置无效")));
        mvc.perform(login(session, Map.of("next", target))).andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(register(session, Map.of("next", target))).andExpect(status().isBadRequest());
        mvc.perform(get("/preview/account").session(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void loginAndRegistrationFailureExamplesAreReadOnlyAndUnknownExamplesReturn400() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/preview/account/login").session(session).param("example", "denied"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("邮箱或密码不正确")));
        mvc.perform(get("/preview/account/register").session(session).param("example", "duplicate"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("该邮箱已注册")));
        mvc.perform(get("/preview/account").session(session)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/preview/account/login").param("example", "unknown")).andExpect(status().isBadRequest());
        mvc.perform(get("/preview/account/register").param("example", "unknown")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"none, 未开通", "active, 有效", "expired, 已过期", "pending, 待确认"})
    void memberExamplesShowNoInventedPriceOrDiscountAndDoNotActivateMembership(String example, String label) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/preview/account/membership").session(session).param("example", example))
                .andExpect(status().isOk()).andExpect(content().string(containsString(label + " · 示例")))
                .andExpect(content().string(containsString("待会员服务报价")))
                .andExpect(content().string(containsString("待会员服务返回")));
        mvc.perform(get("/preview/account").session(session)).andExpect(status().is3xxRedirection());
    }

    @Test
    void joinAndRenewReturnThroughLoginAndHaveNoPaymentMutation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var redirect = mvc.perform(get("/preview/account/membership/join").session(session).param("intent", "renew"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var login = mvc.perform(get(java.net.URI.create(redirect.getResponse().getRedirectedUrl())).session(session))
                .andExpect(status().isOk()).andReturn();
        String target = (String) login.getModelAndView().getModel().get("returnTarget");
        assertThat(target).isEqualTo("/preview/account/membership/join?intent=renew");
        assertThat(login.getModelAndView().getModel().get("returnLabel")).isEqualTo("会员续费页面");
        mvc.perform(login(session, Map.of("next", target))).andExpect(redirectedUrl(target));
        mvc.perform(get(target).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("会员续费页面")))
                .andExpect(content().string(containsString("disabled")));
        mvc.perform(get("/preview/account/membership/join").session(session)).andExpect(status().isOk());
        mvc.perform(post("/preview/account/membership/join").session(session).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/preview/account/membership").param("example", "unknown")).andExpect(status().isBadRequest());
        mvc.perform(get("/preview/account/membership/join").param("intent", "unknown")).andExpect(status().isBadRequest());
    }

    @Test
    void previewLogoutKeepsStayDraftAndRemovesOnlyAccountPresentation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(login(session, Map.of())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/preview/stays").session(session).with(csrf()).param("roomType", "courtyard")
                .param("arrival", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(10).toString())
                .param("departure", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(12).toString())
                .param("rooms", "1").param("guestName", "演示住客").param("guestEmail", "guest@example.invalid"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/preview/account/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/preview/account/login"));
        mvc.perform(get("/preview/account").session(session)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/preview/stays/UI-DRAFT").session(session)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void previewLogoutDoesNotClearActualStaffIdentity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(login(session, Map.of())).andExpect(authenticated().withRoles("STAFF"));
        mvc.perform(post("/preview/account/logout").session(session).with(csrf())).andExpect(authenticated().withRoles("STAFF"));
        mvc.perform(get("/admin/rooms").session(session)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/preview/account/login", "/preview/account/register", "/preview/account/logout"})
    void allPreviewPostsRequireCsrf(String path) throws Exception {
        mvc.perform(post(path)).andExpect(status().isForbidden());
    }

    @Test
    void contactMarkupIsEscapedInRegistrationResult() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var result = mvc.perform(register(session, Map.of("displayName", "<script>alert(1)</script>")))
                .andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get(result.getResponse().getRedirectedUrl()).session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    private MockHttpServletRequestBuilder login(MockHttpSession session, Map<String, String> overrides) {
        var fields = new HashMap<>(Map.of("email", "guest@example.invalid", "password", "UI-ONLY-SECRET", "next", "/preview/account"));
        fields.putAll(overrides);
        var request = post("/preview/account/login").session(session).with(csrf());
        fields.forEach(request::param);
        return request;
    }

    private MockHttpServletRequestBuilder register(MockHttpSession session, Map<String, String> overrides) {
        var fields = new HashMap<>(Map.of("displayName", "演示住客", "email", "guest@example.invalid", "password", "UI-ONLY-SECRET",
                "confirmPassword", "UI-ONLY-SECRET", "next", "/preview/account"));
        fields.putAll(overrides);
        var request = post("/preview/account/register").session(session).with(csrf());
        fields.forEach(request::param);
        return request;
    }
}
