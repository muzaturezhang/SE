package com.holidayresort.booking.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookingStatusTest {

    @Test
    void terminalStatusesCannotBeReopenedAccidentally() {
        assertThat(BookingStatus.PENDING.isTerminal()).isFalse();
        assertThat(BookingStatus.UNDER_REVIEW.isTerminal()).isFalse();
        assertThat(BookingStatus.PAID.isTerminal()).isTrue();
        assertThat(BookingStatus.FAILED.isTerminal()).isTrue();
        assertThat(BookingStatus.EXPIRED.isTerminal()).isTrue();
    }
}
