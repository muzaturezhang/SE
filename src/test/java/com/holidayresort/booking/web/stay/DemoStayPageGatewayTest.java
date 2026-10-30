package com.holidayresort.booking.web.stay;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holidayresort.booking.room.application.DefaultRoomQueryService;
import com.holidayresort.booking.room.application.InvalidRoomSearchException;
import com.holidayresort.booking.room.infrastructure.InMemoryRoomRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;

class DemoStayPageGatewayTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private InMemoryRoomRepository inventory;
    private DemoStayPageGateway gateway;
    private MockHttpSession session;

    @BeforeEach
    void setup() {
        inventory = new InMemoryRoomRepository(new ClassPathResource("fixtures/room-inventory.json"), new ObjectMapper(), clock);
        session = new MockHttpSession();
        gateway = new DemoStayPageGateway(new DefaultRoomQueryService(inventory, clock), clock, session);
    }

    @Test
    void submissionIsOnlySessionDraftAndNeverChangesInventoryOrInventsFinalPrice() {
        var snapshot = inventory.readSnapshot();
        var selection = new StayPageGateway.Selection("courtyard", LocalDate.now(clock).plusDays(10), LocalDate.now(clock).plusDays(12), 2);
        var quote = gateway.quote(selection);
        assertThat(quote.availableRooms()).isEqualTo(4);
        var draft = gateway.submit(session.getId(), new StayPageGateway.Submission(selection, "演示住客", "guest@example.invalid"));
        assertThat(draft.stay().rooms()).isEqualTo(2);
        assertThat(draft.totalMinor()).isNull();
        assertThat(draft.state()).isEqualTo(StayPageGateway.DisplayState.DRAFT);
        assertThat(gateway.listFor(session.getId())).hasSize(6);
        gateway.submit(session.getId(), new StayPageGateway.Submission(selection, "新演示资料", "demo@example.invalid"));
        assertThat(gateway.listFor(session.getId())).hasSize(6);
        assertThat(inventory.readSnapshot()).isSameAs(snapshot);
    }

    @Test
    void sessionsCannotReadOrSubmitOneAnothersDraft() {
        var selection = new StayPageGateway.Selection("pool", LocalDate.now(clock).plusDays(10), LocalDate.now(clock).plusDays(11), 1);
        gateway.submit(session.getId(), new StayPageGateway.Submission(selection, "演示住客", "guest@example.invalid"));
        assertThat(gateway.listFor("other-session")).isEmpty();
        assertThat(gateway.findFor("other-session", "UI-DRAFT")).isEmpty();
        assertThatThrownBy(() -> gateway.submit("other-session", new StayPageGateway.Submission(selection, "演示", "demo@example.invalid")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unavailableAndUnknownTypesAreRejectedWithoutCreatingDraft() {
        LocalDate date = LocalDate.now(clock).plusDays(2);
        assertThatThrownBy(() -> gateway.quote(new StayPageGateway.Selection("pool", date, date.plusDays(2), 1)))
                .isInstanceOfSatisfying(StayUiException.class, error -> assertThat(error.status()).isEqualTo(409));
        assertThatThrownBy(() -> gateway.quote(new StayPageGateway.Selection("all", date, date.plusDays(2), 1)))
                .isInstanceOf(InvalidRoomSearchException.class);
        assertThat(gateway.findFor(session.getId(), "UI-DRAFT")).isEmpty();
    }

    @Test
    void fixtureAmountsAndDeadlinesAreStableAcrossRepeatedReads() {
        var first = gateway.findFor(session.getId(), "DEMO-WAITING").orElseThrow();
        assertThat(first.totalLabel()).isEqualTo("CNY 1,960.00");
        assertThat(first.stay().rateLabel()).isEqualTo("980.00");
        assertThat(first.stay().nights()).isEqualTo(2);
        assertThat(gateway.findFor(session.getId(), "DEMO-WAITING")).contains(first);
        assertThatThrownBy(() -> gateway.listFor(session.getId()).clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void loginSessionIdRotationKeepsOwnDraftAccessibleAndRejectsOldId() {
        String previousId = session.getId();
        var selection = new StayPageGateway.Selection("courtyard", LocalDate.now(clock).plusDays(10), LocalDate.now(clock).plusDays(12), 1);
        gateway.submit(previousId, new StayPageGateway.Submission(selection, "演示住客", "guest@example.invalid"));
        session.changeSessionId();
        assertThat(session.getId()).isNotEqualTo(previousId);
        assertThat(gateway.findFor(session.getId(), "UI-DRAFT")).isPresent();
        assertThat(gateway.findFor(previousId, "UI-DRAFT")).isEmpty();
    }
}
