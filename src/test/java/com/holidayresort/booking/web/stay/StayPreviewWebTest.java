package com.holidayresort.booking.web.stay;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.holidayresort.booking.room.application.RoomManagementService;
import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import com.holidayresort.booking.room.domain.RoomInventory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:booking-ui-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "booking-ui"})
@Import(StayPreviewWebTest.FixedTime.class)
class StayPreviewWebTest {
    @Autowired MockMvc mvc;
    @Autowired RoomInventoryEditor inventory;
    @Autowired RoomManagementService management;
    private RoomInventory initial;

    @BeforeEach void saveInventory() { initial = inventory.readSnapshot(); }
    @AfterEach void restoreInventory() { inventory.updateSnapshot(ignored -> initial); }

    @Test
    void searchCarriesExactSelectionIntoConfirmationWithoutChangingInventory() throws Exception {
        mvc.perform(get("/rooms").param("roomType", "courtyard").param("arrival", "2026-11-07")
                .param("departure", "2026-11-09").param("rooms", "2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("预订预览 庭院客房")))
                .andExpect(content().string(containsString("rooms=2")));
        var result = mvc.perform(get("/preview/stays/new").param("roomType", "courtyard").param("arrival", "2026-11-07")
                .param("departure", "2026-11-09").param("rooms", "2"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("待预订服务报价"))).andReturn();
        var quote = (StayPageGateway.Quote) result.getModelAndView().getModel().get("quote");
        assertThat(quote.stay().rooms()).isEqualTo(2);
        assertThat(quote.availableRooms()).isEqualTo(4);
        assertThat(inventory.readSnapshot()).isSameAs(initial);
    }

    @Test
    void validPostCreatesOnlyOwnSessionDraftAndIgnoresForgedAmountAndOwner() throws Exception {
        MockHttpSession first = new MockHttpSession();
        mvc.perform(validPost(first).param("totalMinor", "1").param("state", "PAID").param("viewerId", "another-owner"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/preview/stays/UI-DRAFT"));
        var result = mvc.perform(get("/preview/stays/UI-DRAFT").session(first)).andExpect(status().isOk())
                .andExpect(content().string(containsString("演示草稿"))).andReturn();
        var draft = (StayPageGateway.Reservation) result.getModelAndView().getModel().get("reservation");
        assertThat(draft.state()).isEqualTo(StayPageGateway.DisplayState.DRAFT);
        assertThat(draft.totalMinor()).isNull();
        mvc.perform(get("/preview/stays/UI-DRAFT/payment").session(first)).andExpect(status().isOk())
                .andExpect(content().string(containsString("disabled")));
        mvc.perform(get("/preview/stays/UI-DRAFT").session(new MockHttpSession())).andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(inventory.readSnapshot()).isSameAs(initial);
    }

    @Test
    void postRejectsInvalidContactAndMissingRequiredFieldsWithoutDraft() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(validPost(session, java.util.Map.of("guestName", "", "guestEmail", "not-an-email")))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("请输入入住人姓名")))
                .andExpect(content().string(containsString("请输入有效的联系邮箱")));
        mvc.perform(post("/preview/stays").session(session).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/preview/stays/UI-DRAFT").session(session)).andExpect(status().isNotFound());
        assertThat(inventory.readSnapshot()).isSameAs(initial);
    }

    @ParameterizedTest
    @CsvSource({"arrival, not-a-date", "arrival, 2026-02-30", "arrival, 2026-10-27", "departure, 2026-11-07",
            "rooms, 0", "rooms, -1", "rooms, 1.5", "rooms, 2147483648", "roomType, missing", "roomType, all"})
    void invalidSelectionCannotBypassServerValidation(String field, String value) throws Exception {
        var params = new java.util.HashMap<>(java.util.Map.of("roomType", "courtyard", "arrival", "2026-11-07", "departure", "2026-11-09", "rooms", "1"));
        params.put(field, value);
        var invalid = get("/preview/stays/new");
        params.forEach(invalid::param);
        mvc.perform(invalid).andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("请检查入住信息")))
                .andExpect(content().string(not(containsString("Failed to convert"))));
        assertThat(inventory.readSnapshot()).isSameAs(initial);
    }

    @Test
    void inventoryChangesBeforePostAreRecheckedAndNoDraftIsCreated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/preview/stays/new").session(session).param("roomType", "courtyard").param("arrival", "2026-11-07")
                .param("departure", "2026-11-09").param("rooms", "4")).andExpect(status().isOk());
        management.disable("C104");
        var changed = inventory.readSnapshot();
        mvc.perform(validPost(session, java.util.Map.of("rooms", "4"))).andExpect(status().isConflict())
                .andExpect(content().string(containsString("连续可用房间不足")));
        mvc.perform(get("/preview/stays/UI-DRAFT").session(session)).andExpect(status().isNotFound());
        assertThat(inventory.readSnapshot()).isSameAs(changed);
    }

    @ParameterizedTest
    @CsvSource({"DEMO-WAITING, 待支付", "DEMO-PAID, 已支付", "DEMO-FAILED, 支付失败", "DEMO-REVIEW, 支付待核对", "DEMO-EXPIRED, 已过期"})
    void everyReadOnlyPaymentScenarioRendersCorrectState(String reference, String label) throws Exception {
        mvc.perform(get("/preview/stays/" + reference + "/payment")).andExpect(status().isOk())
                .andExpect(content().string(containsString(label)))
                .andExpect(content().string(containsString("不会改变订单或支付状态")))
                .andExpect(content().string(not(containsString("name=\"cardNumber\""))));
        mvc.perform(get("/preview/stays/" + reference)).andExpect(status().isOk());
        assertThat(inventory.readSnapshot()).isSameAs(initial);
    }

    @Test
    void listUnknownRecordCsrfAndUnsupportedPaymentMutationsAreHandled() throws Exception {
        mvc.perform(get("/preview/stays")).andExpect(status().isOk())
                .andExpect(content().string(containsString("DEMO-PAID")));
        mvc.perform(get("/preview/stays/missing/payment")).andExpect(status().isNotFound());
        mvc.perform(post("/preview/stays")).andExpect(status().isForbidden());
        mvc.perform(post("/preview/stays/DEMO-WAITING/payment").with(csrf())).andExpect(status().isMethodNotAllowed());
        assertThat(inventory.readSnapshot()).isSameAs(initial);
    }

    @Test
    void emptyListAndOverlongContactHaveExplicitStates() throws Exception {
        mvc.perform(get("/preview/stays").param("example", "empty")).andExpect(status().isOk())
                .andExpect(content().string(containsString("还没有预订记录")));
        mvc.perform(get("/preview/stays").param("example", "unknown")).andExpect(status().isBadRequest());
        mvc.perform(validPost(new MockHttpSession(), java.util.Map.of("guestName", "x".repeat(81))))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("最多 80 个字符")));
        mvc.perform(validPost(new MockHttpSession(), java.util.Map.of("guestEmail", "x".repeat(250) + "@example.invalid")))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("最多 254 个字符")));
    }

    @Test
    void guestInputIsEscapedOnDetailAndNeverRenderedAsMarkup() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(validPost(session, java.util.Map.of("guestName", "<script>alert(1)</script>")))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/preview/stays/UI-DRAFT").session(session)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    private MockHttpServletRequestBuilder validPost(MockHttpSession session) {
        return validPost(session, java.util.Map.of());
    }

    private MockHttpServletRequestBuilder validPost(MockHttpSession session, java.util.Map<String, String> overrides) {
        var params = new java.util.HashMap<>(java.util.Map.of("roomType", "courtyard", "arrival", "2026-11-07",
                "departure", "2026-11-09", "rooms", "1", "guestName", "演示住客", "guestEmail", "guest@example.invalid"));
        params.putAll(overrides);
        var request = post("/preview/stays").session(session).with(csrf());
        params.forEach(request::param);
        return request;
    }

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock stayPreviewClock() {
            return Clock.fixed(Instant.parse("2026-10-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
