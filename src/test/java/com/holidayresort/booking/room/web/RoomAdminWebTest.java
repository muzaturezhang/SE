package com.holidayresort.booking.room.web;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.holidayresort.booking.room.application.RoomManagementService;
import com.holidayresort.booking.room.application.port.RoomInventoryEditor;
import com.holidayresort.booking.room.domain.RoomInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "STAFF")
class RoomAdminWebTest {
    @Autowired MockMvc mvc;
    @Autowired RoomInventoryEditor repository;
    @Autowired RoomManagementService service;
    private RoomInventory initial;

    @BeforeEach void rememberInventory() { initial = repository.readSnapshot(); }
    @AfterEach void restoreInventory() { repository.updateSnapshot(ignored -> initial); }

    @Test
    void staffViewsListFiltersEditorsAndCsrfForms() throws Exception {
        mvc.perform(get("/admin/rooms")).andExpect(status().isOk())
                .andExpect(content().string(containsString("房间清单")))
                .andExpect(content().string(containsString("C101")))
                .andExpect(content().string(containsString("C105")));
        mvc.perform(get("/admin/rooms").param("type", "courtyard").param("state", "disabled"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("C105")))
                .andExpect(content().string(not(containsString("C101"))));
        mvc.perform(get("/admin/rooms/new")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/admin/rooms/C104/edit")).andExpect(status().isOk())
                .andExpect(content().string(containsString("确认停用 C104")));
        mvc.perform(get("/admin/room-types/courtyard/rate")).andExpect(status().isOk())
                .andExpect(content().string(containsString("980.00")));
    }

    @Test
    void createEditDisableAndReenableKeepFixedRoomIdentity() throws Exception {
        mvc.perform(post("/admin/rooms").with(csrf()).param("roomId", "C106")
                .param("roomTypeId", "courtyard").param("enabled", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/rooms"));
        mvc.perform(post("/admin/rooms/C106").with(csrf()).param("roomId", "FORGED")
                .param("roomTypeId", "pool").param("enabled", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(service.get("C106").room().roomTypeId()).isEqualTo("pool");
        assertThat(repository.readSnapshot().rooms()).noneMatch(room -> room.id().equals("FORGED"));
        mvc.perform(post("/admin/rooms/C106/disable").with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(service.get("C106").room().enabled()).isFalse();
        mvc.perform(post("/admin/rooms/C106").with(csrf()).param("roomTypeId", "pool").param("enabled", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(service.get("C106").room().enabled()).isTrue();
        assertThat(repository.readSnapshot().blocks()).isEqualTo(initial.blocks());
    }

    @Test
    void duplicateAndActiveOccupancyReturnConflictWithoutMutation() throws Exception {
        mvc.perform(post("/admin/rooms").with(csrf()).param("roomId", "C101").param("roomTypeId", "courtyard"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("这个房号已存在")));
        mvc.perform(post("/admin/rooms/C101/disable").with(csrf()))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("当前或未来占用")));
        mvc.perform(post("/admin/rooms/C101").with(csrf()).param("roomTypeId", "pool").param("enabled", "true"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("当前或未来占用")));
        assertThat(repository.readSnapshot()).isEqualTo(initial);
    }

    @Test
    void invalidRoomFieldsAreRejectedAndSafeValuesRetained() throws Exception {
        mvc.perform(post("/admin/rooms").with(csrf()).param("roomId", "<script>alert(1)</script>")
                .param("roomTypeId", "unknown"))
                .andExpect(status().isBadRequest()).andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        mvc.perform(post("/admin/rooms").with(csrf()).param("roomId", "C106").param("roomTypeId", "unknown"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("请选择已有的房型")))
                .andExpect(content().string(containsString("C106")));
        mvc.perform(post("/admin/rooms").with(csrf()).param("roomId", "C106").param("roomTypeId", "pool").param("enabled", "garbage"))
                .andExpect(status().isBadRequest());
        assertThat(repository.readSnapshot()).isSameAs(initial);
    }

    @Test
    void unknownRecordsAndInvalidFiltersHaveReadableErrors() throws Exception {
        mvc.perform(get("/admin/rooms/missing/edit")).andExpect(status().isNotFound());
        mvc.perform(post("/admin/rooms/missing/disable").with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/admin/room-types/missing/rate")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/rooms").param("state", "bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/rooms").param("type", "bad")).andExpect(status().isBadRequest());
        mvc.perform(delete("/admin/rooms/C104").with(csrf())).andExpect(status().isMethodNotAllowed());
        assertThat(repository.readSnapshot()).isSameAs(initial);
    }

    @Test
    void staffRateUpdateIsVisibleOnGuestPage() throws Exception {
        mvc.perform(post("/admin/room-types/courtyard/rate").with(csrf()).param("nightlyRate", "1234.56"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/rooms").param("roomType", "courtyard")).andExpect(status().isOk())
                .andExpect(content().string(containsString("1,234.56")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "1.999", "abc", "92233720368547758.08"})
    void invalidRatesReturn400WithoutSaving(String value) throws Exception {
        mvc.perform(post("/admin/room-types/courtyard/rate").with(csrf()).param("nightlyRate", value))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("请检查房价")));
        assertThat(repository.readSnapshot()).isSameAs(initial);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/rooms", "/admin/rooms/C104", "/admin/rooms/C104/disable", "/admin/room-types/courtyard/rate"})
    void everyMutationRequiresCsrfEvenForStaff(String url) throws Exception {
        mvc.perform(post(url)).andExpect(status().isForbidden());
        assertThat(repository.readSnapshot()).isSameAs(initial);
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotReadOrMutateAndDefaultProfileHasNoPreviewLogin() throws Exception {
        assertNoAccess();
        mvc.perform(get("/staff-preview/login")).andExpect(status().isForbidden());
        mvc.perform(get("/rooms")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("本地管理预览 ↗"))));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotReadOrMutate() throws Exception { assertNoAccess(); }

    private void assertNoAccess() throws Exception {
        mvc.perform(get("/admin/rooms")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/rooms/C104/edit")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/room-types/courtyard/rate")).andExpect(status().isForbidden());
        for (String url : new String[] {"/admin/rooms", "/admin/rooms/C104", "/admin/rooms/C104/disable", "/admin/room-types/courtyard/rate"}) {
            mvc.perform(post(url).with(csrf()).param("roomId", "C106").param("roomTypeId", "pool").param("nightlyRate", "1"))
                    .andExpect(status().isForbidden());
        }
        assertThat(repository.readSnapshot()).isSameAs(initial);
    }
}
