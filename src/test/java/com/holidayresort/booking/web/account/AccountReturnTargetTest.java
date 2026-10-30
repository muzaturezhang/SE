package com.holidayresort.booking.web.account;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountReturnTargetTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void emptyTargetReturnsToProfile(String value) {
        assertThat(AccountReturnTarget.checked(value)).isEqualTo("/preview/account");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/preview/account", "/preview/account/membership", "/preview/account/membership/join?intent=renew",
            "/preview/stays", "/preview/stays/new", "/preview/stays/new?roomType=courtyard&arrival=2026-11-07&departure=2026-11-09&rooms=2"})
    void knownLocalTargetsKeepTheirExactSelection(String value) {
        assertThat(AccountReturnTarget.checked(value)).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.invalid", "//example.invalid", "javascript:alert(1)", "/admin/rooms",
            "/preview/account/../account", "/preview/account#fragment", "/preview/account?roles=STAFF",
            "/preview/stays/new?next=https://example.invalid", "/preview/stays/new?rooms=1&rooms=2",
            "/preview/stays/new?roomType=%2f%2fexample.invalid", "/preview/account\\evil", "/%2fexample.invalid",
            "preview/account", "/preview/account\r\nLocation:https://example.invalid", "/preview/stays/new?rooms=1&"})
    void externalEncodedControlOrUnexpectedTargetsAreRejected(String value) {
        assertThatThrownBy(() -> AccountReturnTarget.checked(value)).isInstanceOf(AccountUiException.class);
    }
}
