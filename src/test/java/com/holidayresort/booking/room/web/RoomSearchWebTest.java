package com.holidayresort.booking.room.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RoomSearchWebTest.FixedTime.class)
class RoomSearchWebTest {

    @Autowired
    MockMvc mvc;

    @Test
    void catalogAndSingleTypeBrowsingAreBackendDrivenWithoutDates() throws Exception {
        var model = mvc.perform(get("/rooms"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat(cards(model.get("roomCards"))).hasSize(3);
        assertThat(model.get("searchPerformed")).isEqualTo(false);
        assertThat(cards(model.get("roomCards"))).allMatch(card -> card.availableRooms() == null);
        var filtered = mvc.perform(get("/rooms").param("roomType", "pool"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat(cards(filtered.get("roomCards"))).extracting(card -> card.type().id()).containsExactly("pool");
    }

    @Test
    void returnsRoomsAvailableForTheWholePeriodWithCorrectBaseRate() throws Exception {
        var result = mvc.perform(get("/rooms").param("arrival", "2026-10-29")
                        .param("departure", "2026-10-31").param("rooms", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stayNights", 2L))
                .andExpect(model().attribute("searchPerformed", true))
                .andExpect(content().string(containsString("980.00")))
                .andReturn().getModelAndView().getModel();
        assertThat(cards(result.get("roomCards"))).hasSize(1);
        var card = cards(result.get("roomCards")).get(0);
        assertThat(card.type().id()).isEqualTo("courtyard");
        assertThat(card.availableRooms()).isEqualTo(2);
        assertThat(card.type().nightlyRateMinor()).isEqualTo(98000);
    }

    @Test
    void supportsAdjacentDatesAndDoesNotIncludeDisabledRooms() throws Exception {
        var result = mvc.perform(get("/rooms").param("arrival", "2026-10-31")
                        .param("departure", "2026-11-02").param("rooms", "1"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat(cards(result.get("roomCards"))).extracting(RoomCardView::availableRooms).containsExactly(2, 2, 1);
        var noBlocks = mvc.perform(get("/rooms").param("arrival", "2026-11-10")
                        .param("departure", "2026-11-12").param("rooms", "1"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
        assertThat(cards(noBlocks.get("roomCards"))).extracting(RoomCardView::availableRooms).containsExactly(4, 2, 1);
    }

    @Test
    void showsAnEmptyStateInsteadOfInventingAvailability() throws Exception {
        mvc.perform(get("/rooms").param("arrival", "2026-10-29")
                        .param("departure", "2026-11-02").param("rooms", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("roomCards", List.of()))
                .andExpect(content().string(containsString("暂未找到符合条件的居所")));
        mvc.perform(get("/rooms").param("arrival", "2026-10-29")
                        .param("departure", "2026-10-31").param("rooms", "1").param("roomType", "pool"))
                .andExpect(status().isOk()).andExpect(model().attribute("roomCards", List.of()));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "2026-10-26|2026-10-29|1|all",
            "2026-10-29|2026-10-29|1|all",
            "2026-10-30|2026-10-29|1|all",
            "2026-02-30|2026-10-29|1|all",
            "invalid|2026-10-29|1|all",
            "2026-10-29|invalid|1|all",
            "2026-10-29|2026-10-31|0|all",
            "2026-10-29|2026-10-31|-1|all",
            "2026-10-29|2026-10-31|1.5|all",
            "2026-10-29|2026-10-31|abc|all",
            "2026-10-29|2026-10-31|2147483648|all",
            "2026-10-29|2026-10-31|1|missing"
    }, delimiter = '|')
    void rejectsInvalidQueriesEvenWithoutBrowserValidation(String arrival, String departure, String rooms, String type) throws Exception {
        mvc.perform(get("/rooms").param("arrival", arrival).param("departure", departure)
                        .param("rooms", rooms).param("roomType", type))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("roomCards", List.of()))
                .andExpect(content().string(containsString("请检查查询条件")));
    }

    @Test
    void rejectsIncompleteQueriesAndUnknownCatalogTypes() throws Exception {
        mvc.perform(get("/rooms").param("arrival", "2026-10-29"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("请选择退房日期")));
        mvc.perform(get("/rooms").param("rooms", ""))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/rooms").param("roomType", "missing"))
                .andExpect(status().isBadRequest());
    }

    @SuppressWarnings("unchecked")
    private static List<RoomCardView> cards(Object value) {
        return (List<RoomCardView>) value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-27T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
