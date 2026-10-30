package com.holidayresort.booking.web.staffstay;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class DemoStaffStayPageGatewayTest {
    private final DemoStaffStayPageGateway gateway = new DemoStaffStayPageGateway(
            Clock.fixed(Instant.parse("2026-10-29T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
    private StaffStayPageGateway.Query all() { return new StaffStayPageGateway.Query("", null, null); }

    @Test void filtersAreCombinedAndReferenceIsCaseInsensitive() {
        var query = new StaffStayPageGateway.Query(" staff-demo-002 ", LocalDate.of(2026, 11, 8), StaffStayPageGateway.State.CONFIRMED);
        assertThat(gateway.search("staff", query)).extracting(StaffStayPageGateway.Entry::reference).containsExactly("STAFF-DEMO-002");
        assertThat(gateway.search("staff", new StaffStayPageGateway.Query("002", LocalDate.of(2026, 11, 9), null))).isEmpty();
        assertThat(gateway.search("staff", new StaffStayPageGateway.Query("", null, StaffStayPageGateway.State.WAITING))).hasSize(2);
    }
    @Test void snapshotsStayUnchangedAcrossReadsAndUnknownRecordsAreAbsent() {
        var first = gateway.search("staff", all());
        gateway.search("staff", new StaffStayPageGateway.Query("missing", null, null));
        assertThat(gateway.search("staff", all())).isEqualTo(first).hasSize(6);
        assertThatThrownBy(() -> first.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(gateway.find("staff", "UI-DRAFT")).isEmpty();
    }
    @Test void amountsAreFormattedAsExactCnyAndNullIsNotZero() {
        assertThat(gateway.find("staff", "STAFF-DEMO-002").orElseThrow().totalLabel()).isEqualTo("CNY 3,360.00");
        var unknown = gateway.find("staff", "STAFF-DEMO-006").orElseThrow();
        assertThat(unknown.totalMinor()).isNull();
        assertThat(unknown.totalLabel()).isEqualTo("待预订服务报价");
        assertThat(unknown.nights()).isEqualTo(2);
    }
    @Test void missingStaffIdentityIsRejectedByFixtureBoundary() {
        assertThatThrownBy(() -> gateway.search(null, all())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gateway.find(" ", "STAFF-DEMO-001")).isInstanceOf(AccessDeniedException.class);
    }
    @Test void queryRejectsMarkupAndExcessLengthAndNormalizesNull() {
        assertThatThrownBy(() -> new StaffStayPageGateway.Query("<script>", null, null)).isInstanceOf(StaffStayUiException.class);
        assertThatThrownBy(() -> new StaffStayPageGateway.Query("A".repeat(41), null, null)).isInstanceOf(StaffStayUiException.class);
        assertThat(new StaffStayPageGateway.Query(null, null, null).reference()).isEmpty();
    }
}
