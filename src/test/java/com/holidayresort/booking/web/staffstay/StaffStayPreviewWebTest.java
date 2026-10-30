package com.holidayresort.booking.web.staffstay;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:staff-booking-ui-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "staff-booking-ui"})
@Import(StaffStayPreviewWebTest.FixedTime.class)
class StaffStayPreviewWebTest {
    @Autowired MockMvc mvc;
    @Autowired RoomInventoryEditor inventory;
    @MockitoSpyBean DemoStaffStayPageGateway gateway;

    @Test void anonymousAndCustomerCannotReadListOrDetails() throws Exception {
        for (String path : new String[]{"/admin/stays", "/admin/stays/STAFF-DEMO-001"}) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
            mvc.perform(get(path).with(user("guest").roles("CUSTOMER"))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(gateway);
    }
    @Test void listHasNavigationAndNoStoreAndUsesServerPrincipal() throws Exception {
        var result = mvc.perform(get("/admin/stays").with(user("real-staff").roles("STAFF")).param("staffId", "forged"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("6 条演示记录")))
                .andExpect(content().string(containsString("虚构记录")))
                .andExpect(content().string(containsString("href=\"/admin/rooms\"")))
                .andExpect(content().string(containsString("aria-current=\"page\">预订管理"))).andReturn();
        assertThat(result.getModelAndView().getModel().get("entries")).asList().hasSize(6);
        verify(gateway).search(eq("real-staff"), any());
    }
    @Test void combinedFilterAndDetailReturnKeepSelection() throws Exception {
        mvc.perform(get("/admin/stays").with(user("staff").roles("STAFF")).param("reference", "002")
                .param("arrival", "2026-11-08").param("state", "CONFIRMED"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("1 条演示记录")))
                .andExpect(content().string(containsString("/admin/stays/STAFF-DEMO-002?reference=002&amp;arrival=2026-11-08&amp;state=CONFIRMED")))
                .andExpect(content().string(not(containsString("STAFF-DEMO-001"))));
        mvc.perform(get("/admin/stays/STAFF-DEMO-002").with(user("staff").roles("STAFF"))
                .param("reference", "002").param("arrival", "2026-11-08").param("state", "CONFIRMED"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/admin/stays?reference=002&amp;arrival=2026-11-08&amp;state=CONFIRMED")))
                .andExpect(content().string(containsString("CNY 3,360.00")))
                .andExpect(content().string(containsString("已支付")));
    }
    @ParameterizedTest
    @CsvSource({"reference,<script>alert(1)</script>", "arrival,2026-02-30", "arrival,2026-1-1", "state,UNKNOWN", "state,''"})
    void invalidFiltersAreReadableAndSafe(String field, String value) throws Exception {
        mvc.perform(get("/admin/stays").with(user("staff").roles("STAFF")).param(field, value))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("role=\"alert\"")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(not(containsString("DateTimeParseException"))));
    }
    @Test void excessiveReferenceAndHistoricalEmptyResultsAreHandled() throws Exception {
        mvc.perform(get("/admin/stays").with(user("staff").roles("STAFF")).param("reference", "A".repeat(41)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/admin/stays").with(user("staff").roles("STAFF")).param("arrival", "2020-01-01"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("没有符合条件的预订")));
    }
    @ParameterizedTest
    @CsvSource({"001,待支付", "002,已确认", "003,支付失败", "004,支付待核对", "005,已过期", "006,待预订服务报价"})
    void allDetailStatesAreReadOnlyAndDoNotWriteInventory(String suffix, String label) throws Exception {
        var before = inventory.readSnapshot();
        mvc.perform(get("/admin/stays/STAFF-DEMO-" + suffix).with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString(label)))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(containsString("待预订服务提供")));
        assertThat(inventory.readSnapshot()).isSameAs(before);
    }
    @Test void missingRecordHasReadableErrorAndActiveNavigation() throws Exception {
        mvc.perform(get("/admin/stays/UI-DRAFT").with(user("staff").roles("STAFF")))
                .andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("找不到这条演示预订")))
                .andExpect(content().string(containsString("aria-current=\"page\">预订管理")));
    }
    @Test void noMutationEndpointExistsAndCsrfIsStillRequired() throws Exception {
        var before = inventory.readSnapshot();
        mvc.perform(post("/admin/stays/STAFF-DEMO-001").with(user("staff").roles("STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/stays/STAFF-DEMO-001").with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/admin/stays").with(user("staff").roles("STAFF")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        assertThat(inventory.readSnapshot()).isSameAs(before);
    }
    @Test void serviceContactProjectionIsEscapedInDetail() throws Exception {
        var entry = new StaffStayPageGateway.Entry("STAFF-DEMO-001", "<script>alert(1)</script>", "<b>email</b>",
                "庭院客房", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10), 1, null, "待返回", StaffStayPageGateway.State.WAITING);
        doReturn(Optional.of(entry)).when(gateway).find("staff", "STAFF-DEMO-001");
        mvc.perform(get("/admin/stays/STAFF-DEMO-001").with(user("staff").roles("STAFF")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }
    @Test void roomPagesShowCrossModuleNavigation() throws Exception {
        mvc.perform(get("/admin/rooms").with(user("staff").roles("STAFF"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/admin/stays\"")))
                .andExpect(content().string(containsString("aria-current=\"page\">房间管理")));
    }
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock staffStayClock() {
            return Clock.fixed(Instant.parse("2026-10-29T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
