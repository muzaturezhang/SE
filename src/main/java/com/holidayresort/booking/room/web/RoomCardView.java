package com.holidayresort.booking.room.web;

import com.holidayresort.booking.room.domain.RoomType;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public record RoomCardView(RoomType type, Integer availableRooms, String rateLabel) {

    public static RoomCardView of(RoomType type, Integer availableRooms) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return new RoomCardView(type, availableRooms,
                format.format(BigDecimal.valueOf(type.nightlyRateMinor(), 2)));
    }
}
