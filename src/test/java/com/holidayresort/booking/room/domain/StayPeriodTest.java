package com.holidayresort.booking.room.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StayPeriodTest {

    private final StayPeriod stay = period("2026-11-02", "2026-11-05");

    @ParameterizedTest
    @CsvSource({
            "2026-11-01,2026-11-02,false",
            "2026-11-05,2026-11-06,false",
            "2026-11-01,2026-11-03,true",
            "2026-11-04,2026-11-06,true",
            "2026-11-03,2026-11-04,true",
            "2026-11-01,2026-11-06,true",
            "2026-11-02,2026-11-05,true"
    })
    void overlapsOnlyWhenAnOccupiedNightIsShared(String arrival, String departure, boolean overlap) {
        StayPeriod other = period(arrival, departure);
        assertThat(stay.overlaps(other)).isEqualTo(overlap);
        assertThat(other.overlaps(stay)).isEqualTo(overlap);
    }

    @Test
    void countsNightsAcrossLeapDayAndMonthBoundary() {
        assertThat(period("2028-02-28", "2028-03-01").nights()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"2026-11-02,2026-11-02", "2026-11-03,2026-11-02"})
    void rejectsEmptyOrReversedStays(String arrival, String departure) {
        assertThatThrownBy(() -> period(arrival, departure)).isInstanceOf(IllegalArgumentException.class);
    }

    private static StayPeriod period(String arrival, String departure) {
        return new StayPeriod(LocalDate.parse(arrival), LocalDate.parse(departure));
    }
}
