package com.holidayresort.booking.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "LOCAL_STAFF_USERNAME=preview-test", "LOCAL_STAFF_PASSWORD=test-only-password-never-for-deployment",
        "spring.datasource.url=jdbc:h2:mem:staff-preview-test;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "local-staff"})
class LocalStaffPreviewWebTest {
    @Autowired MockMvc mvc;

    @Test
    void localLoginGrantsStaffSessionAndPostLogoutRemovesAccess() throws Exception {
        mvc.perform(get("/admin/rooms")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/staff-preview/login"));
        mvc.perform(get("/staff-preview/login")).andExpect(status().isOk());
        var result = mvc.perform(post("/staff-preview/login").with(csrf()).param("username", "preview-test")
                        .param("password", "test-only-password-never-for-deployment"))
                .andExpect(authenticated().withRoles("STAFF"))
                .andExpect(redirectedUrl("/admin/rooms")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/admin/rooms").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(unauthenticated())
                .andExpect(redirectedUrl("/rooms"));
        mvc.perform(get("/admin/rooms")).andExpect(status().is3xxRedirection());
    }

    @Test
    void wrongPasswordAndLoginWithoutCsrfDoNotAuthenticate() throws Exception {
        mvc.perform(post("/staff-preview/login").with(csrf()).param("username", "preview-test").param("password", "wrong"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/staff-preview/login?error"));
        mvc.perform(post("/staff-preview/login").param("username", "preview-test")
                .param("password", "test-only-password-never-for-deployment"))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
    }
}
