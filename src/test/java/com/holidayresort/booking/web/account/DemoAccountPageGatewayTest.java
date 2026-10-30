package com.holidayresort.booking.web.account;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;

class DemoAccountPageGatewayTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private MockHttpSession session;
    private DemoAccountPageGateway gateway;

    @BeforeEach void setup() {
        session = new MockHttpSession();
        gateway = new DemoAccountPageGateway(session, clock);
    }

    @Test
    void registrationKeepsOnlyContactAndDoesNotSignIn() {
        gateway.previewRegistration(session.getId(), " 演示甲 ", "guest@example.invalid");
        assertThat(gateway.registrationFor(session.getId())).contains(new AccountPageGateway.Profile("演示甲", "guest@example.invalid"));
        assertThat(gateway.profileFor(session.getId())).isEmpty();
        gateway.previewSignIn(session.getId(), "guest@example.invalid");
        assertThat(gateway.profileFor(session.getId())).contains(new AccountPageGateway.Profile("演示甲", "guest@example.invalid"));
    }

    @Test
    void anotherViewerCannotReadOrMutateSessionPresentation() {
        gateway.previewSignIn(session.getId(), "guest@example.invalid");
        assertThat(gateway.profileFor("other")).isEmpty();
        assertThat(gateway.registrationFor("other")).isEmpty();
        assertThatThrownBy(() -> gateway.previewRegistration("other", "演示", "demo@example.invalid"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gateway.previewSignIn("other", "demo@example.invalid")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> gateway.clearPreview("other")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void idRotationKeepsOwnPresentationAndClearLeavesOtherSessionData() {
        String previous = session.getId();
        gateway.previewSignIn(previous, "guest@example.invalid");
        session.setAttribute("other-module-draft", "untouched");
        session.changeSessionId();
        assertThat(gateway.profileFor(previous)).isEmpty();
        assertThat(gateway.profileFor(session.getId())).isPresent();
        gateway.clearPreview(session.getId());
        assertThat(gateway.profileFor(session.getId())).isEmpty();
        assertThat(session.getAttribute("other-module-draft")).isEqualTo("untouched");
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "active", "expired", "pending"})
    void membershipExamplesNeverInventFeeOrDiscountOrChangeProfile(String example) {
        var first = gateway.membershipExample(example);
        assertThat(first.feeMinor()).isNull();
        assertThat(first.feeLabel()).isEqualTo("待会员服务报价");
        assertThat(first.discountLabel()).isEqualTo("待会员服务返回");
        assertThat(gateway.membershipExample(example)).isEqualTo(first);
        assertThat(gateway.profileFor(session.getId())).isEmpty();
    }

    @Test
    void unknownMemberExampleIsAReadable400() {
        assertThatThrownBy(() -> gateway.membershipExample("STAFF"))
                .isInstanceOfSatisfying(AccountUiException.class, error -> assertThat(error.status()).isEqualTo(400));
    }
}
