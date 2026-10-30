package com.holidayresort.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FoundationIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    DataSource dataSource;

    @Test
    void publicPagesRenderWithResolvedSharedLayout() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("海岸度假居所")))
                .andExpect(content().string(containsString("/vendor/bootstrap/bootstrap.min.css")));
        mvc.perform(get("/rooms"))
                .andExpect(status().isOk())
                .andExpect(view().name("room/index"))
                .andExpect(content().string(containsString("开发用示例房源")));
    }

    @Test
    void healthAndLocalAssetsArePublicButManagementDetailsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/vendor/bootstrap/bootstrap.min.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/css/app.css")).andExpect(status().isOk());
        mvc.perform(get("/images/suite-interior.jpg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/jpeg"));
        mvc.perform(get("/admin/rooms")).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env")).andExpect(status().isForbidden());
    }

    @Test
    void unsafeRequestsWithoutCsrfAreRejected() throws Exception {
        mvc.perform(post("/rooms")).andExpect(status().isForbidden());
    }

    @Test
    void defaultEnvironmentDoesNotExposeBookingUiFixtures() throws Exception {
        mvc.perform(get("/preview/stays/new")).andExpect(status().isForbidden());
        mvc.perform(get("/preview/stays/DEMO-PAID/payment")).andExpect(status().isForbidden());
        mvc.perform(get("/rooms")).andExpect(content().string(org.hamcrest.Matchers.not(containsString("预订流程预览"))));
    }

    @Test
    void defaultEnvironmentDoesNotExposeAccountUiFixtures() throws Exception {
        mvc.perform(get("/preview/account/login")).andExpect(status().isForbidden());
        mvc.perform(get("/preview/account/membership")).andExpect(status().isForbidden());
        mvc.perform(get("/rooms")).andExpect(content().string(org.hamcrest.Matchers.not(containsString("账户与会员预览"))));
    }

    @Test
    void defaultEnvironmentDoesNotInstallStaffBookingFixtures() throws Exception {
        mvc.perform(get("/admin/stays")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/stays").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user("staff").roles("STAFF"))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/rooms").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user("staff").roles("STAFF"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/admin/stays\""))));
    }

    @Test
    void testProfileUsesAnIsolatedH2Database() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:resort-test");
            assertThat(connection.isValid(2)).isTrue();
        }
    }
}
