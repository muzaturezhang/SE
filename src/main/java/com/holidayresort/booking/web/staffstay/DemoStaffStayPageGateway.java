package com.holidayresort.booking.web.staffstay;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Independent synthetic staff fixtures, unrelated to customer Session drafts or Room inventory. */
@Component
@Profile("staff-booking-ui")
public class DemoStaffStayPageGateway implements StaffStayPageGateway {
    private final List<Entry> entries;
    public DemoStaffStayPageGateway(Clock clock) {
        LocalDate date = LocalDate.now(clock).plusDays(10);
        entries = List.of(
                example("STAFF-DEMO-001", "示例住客一", "庭院客房", date, 196000L, State.WAITING),
                example("STAFF-DEMO-002", "示例住客二", "露台套房", date, 336000L, State.CONFIRMED),
                example("STAFF-DEMO-003", "示例住客三", "庭院客房", date.plusDays(1), 196000L, State.FAILED),
                example("STAFF-DEMO-004", "示例住客四", "泳池别墅", date.plusDays(1), 656000L, State.REVIEW),
                example("STAFF-DEMO-005", "示例住客五", "庭院客房", date.plusDays(2), 196000L, State.EXPIRED),
                example("STAFF-DEMO-006", "示例住客六", "露台套房", date.plusDays(2), null, State.WAITING));
    }
    private Entry example(String ref, String name, String type, LocalDate arrival, Long total, State state) {
        return new Entry(ref, name, "guest@example.invalid", type, arrival, arrival.plusDays(2), 1,
                total, "待会员 / 预订服务返回", state);
    }
    @Override public List<Entry> search(String staffId, Query query) {
        requireStaff(staffId);
        return entries.stream().filter(entry -> entry.reference().contains(query.reference()))
                .filter(entry -> query.arrival() == null || entry.arrival().equals(query.arrival()))
                .filter(entry -> query.state() == null || entry.state() == query.state()).toList();
    }
    @Override public Optional<Entry> find(String staffId, String reference) {
        requireStaff(staffId);
        return entries.stream().filter(entry -> entry.reference().equals(reference)).findFirst();
    }
    private void requireStaff(String staffId) {
        if (staffId == null || staffId.isBlank()) throw new AccessDeniedException("STAFF identity required");
        // HTTP requires ROLE_STAFF. This fixture check is not a replacement for A/C service authorization.
    }
}
