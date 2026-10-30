package com.holidayresort.booking.web.stay;

import static com.holidayresort.booking.web.stay.StayPageGateway.DisplayState.*;

import com.holidayresort.booking.room.application.InvalidRoomSearchException;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.application.RoomSearchQuery;
import com.holidayresort.booking.room.domain.RoomType;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/** Opt-in UI fixture adapter. Never writes Room, Booking or Payment data or calculates final pricing. */
@Component
@SessionScope
@Profile("booking-ui")
public class DemoStayPageGateway implements StayPageGateway {
    private final RoomQueryService rooms;
    private final HttpSession session;
    private final List<Reservation> examples;
    private Reservation draft;

    public DemoStayPageGateway(RoomQueryService rooms, Clock clock, HttpSession session) {
        this.rooms = rooms;
        this.session = session;
        LocalDate date = LocalDate.now(clock).plusDays(10);
        LocalDateTime time = LocalDateTime.now(clock);
        // Pre-authored amounts are UI fixtures, not a pricing algorithm or an actual paid transaction.
        this.examples = List.of(
                example("DEMO-WAITING", "courtyard", date, 2, 98000, 196000, WAITING, time.plusMinutes(15)),
                example("DEMO-PAID", "terrace", date, 2, 168000, 336000, PAID, null),
                example("DEMO-FAILED", "courtyard", date, 2, 98000, 196000, FAILED, null),
                example("DEMO-REVIEW", "pool", date, 1, 328000, 328000, REVIEW, null),
                example("DEMO-EXPIRED", "courtyard", date, 2, 98000, 196000, EXPIRED, time.minusMinutes(5)));
    }

    @Override
    public Quote quote(Selection selection) {
        if (selection.roomType() == null || selection.roomType().isBlank() || selection.roomType().equals("all")) {
            throw new InvalidRoomSearchException("roomType", "请选择一种具体房型。");
        }
        var result = rooms.search(new RoomSearchQuery(selection.arrival(), selection.departure(),
                selection.rooms(), selection.roomType()));
        if (result.options().isEmpty()) {
            throw new StayUiException(409, "所选日期的连续可用房间不足，请调整入住安排后重试。");
        }
        var option = result.options().get(0);
        return new Quote(summary(option.roomType(), selection, option.roomType().nightlyRateMinor()),
                option.availableRooms(), null, "待会员 / 预订服务返回");
    }

    @Override
    public synchronized Reservation submit(String viewerId, Submission submission) {
        requireViewer(viewerId);
        Quote quote = quote(submission.selection()); // Recheck current Room input without reserving anything.
        draft = new Reservation("UI-DRAFT", quote.stay(), submission.guestName().strip(), submission.guestEmail().strip(),
                null, "待会员 / 预订服务返回", DRAFT, null);
        return draft;
    }

    @Override
    public synchronized List<Reservation> listFor(String viewerId) {
        if (!session.getId().equals(viewerId)) return List.of();
        List<Reservation> result = new ArrayList<>();
        if (draft != null) result.add(draft);
        result.addAll(examples);
        return List.copyOf(result);
    }

    @Override
    public Optional<Reservation> findFor(String viewerId, String reference) {
        return listFor(viewerId).stream().filter(item -> item.reference().equals(reference)).findFirst();
    }

    private void requireViewer(String viewerId) {
        if (!session.getId().equals(viewerId)) throw new AccessDeniedException("Preview session does not match");
    }

    private Reservation example(String reference, String typeId, LocalDate arrival, int nights,
            long nightly, long total, DisplayState state, LocalDateTime deadline) {
        RoomType type = rooms.listRoomTypes().stream().filter(item -> item.id().equals(typeId)).findFirst().orElseThrow();
        StaySummary stay = summary(type, new Selection(typeId, arrival, arrival.plusDays(nights), 1), nightly);
        return new Reservation(reference, stay, "演示住客", "guest@example.invalid", total,
                "固定展示示例 · 未应用会员折扣", state, deadline);
    }

    private StaySummary summary(RoomType type, Selection selection, long nightlyRate) {
        return new StaySummary(type.id(), type.name(), type.imagePath(), type.imageAlt(), selection.arrival(),
                selection.departure(), selection.rooms(), nightlyRate);
    }
}
