package com.holidayresort.booking.web.stay;

import com.holidayresort.booking.room.domain.StayPeriod;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** B's page-facing draft contract. These are UI projections, not C's domain states or entities. */
public interface StayPageGateway {
    Quote quote(Selection selection);
    Reservation submit(String viewerId, Submission submission);
    List<Reservation> listFor(String viewerId);
    Optional<Reservation> findFor(String viewerId, String reference);

    record Selection(String roomType, LocalDate arrival, LocalDate departure, int rooms) { }
    record Submission(Selection selection, String guestName, String guestEmail) { }
    record Quote(StaySummary stay, int availableRooms, Long totalMinor, String discountLabel) {
        public String totalLabel() { return totalMinor == null ? "待预订服务报价" : "CNY " + money(totalMinor); }
    }

    record StaySummary(String roomTypeId, String roomTypeName, String imagePath, String imageAlt,
                       LocalDate arrival, LocalDate departure, int rooms, long nightlyRateMinor) {
        public long nights() { return new StayPeriod(arrival, departure).nights(); }
        public String rateLabel() { return money(nightlyRateMinor); }
    }

    enum DisplayState {
        DRAFT("演示草稿", "尚未创建实际订单，也没有占用房间。"),
        WAITING("待支付", "这是待支付页面的固定示例，当前没有实际库存占用。"),
        PAID("已支付", "这是支付成功后的展示示例，没有发生真实扣款。"),
        FAILED("支付失败", "这是失败提示示例；真实重试须以支付服务的结果为准。"),
        REVIEW("支付待核对", "真实场景应先核对支付结果，再决定是否重试或释放库存。"),
        EXPIRED("已过期", "这是过期页面示例；真实超时与库存释放由预订服务处理。");

        private final String label;
        private final String description;
        DisplayState(String label, String description) { this.label = label; this.description = description; }
        public String label() { return label; }
        public String description() { return description; }
    }

    record Reservation(String reference, StaySummary stay, String guestName, String guestEmail,
                       Long totalMinor, String discountLabel, DisplayState state, LocalDateTime paymentDeadline) {
        public String totalLabel() { return totalMinor == null ? "待预订服务报价" : "CNY " + money(totalMinor); }
        public boolean draft() { return state == DisplayState.DRAFT; }
    }

    private static String money(long minor) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(BigDecimal.valueOf(minor, 2));
    }
}
