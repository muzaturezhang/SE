package com.holidayresort.booking.web.staffstay;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** B's read-only page projections; C/A must review the real service and authorization mapping. */
public interface StaffStayPageGateway {
    List<Entry> search(String staffId, Query query);
    Optional<Entry> find(String staffId, String reference);

    record Query(String reference, LocalDate arrival, State state) {
        public Query {
            reference = reference == null ? "" : reference.strip().toUpperCase(Locale.ROOT);
            if (!reference.matches("[A-Z0-9-]{0,40}"))
                throw new StaffStayUiException(400, "预订编号须为最多 40 位字母、数字或短横线。");
        }
    }

    enum State {
        WAITING("待支付", "待支付", "待支付"),
        CONFIRMED("已确认", "已确认", "已支付"),
        FAILED("支付失败", "待支付", "支付失败"),
        REVIEW("支付待核对", "待核对", "结果待核对"),
        EXPIRED("已过期", "已过期", "未完成支付");
        private final String label;
        private final String bookingLabel;
        private final String paymentLabel;
        State(String label, String bookingLabel, String paymentLabel) {
            this.label = label; this.bookingLabel = bookingLabel; this.paymentLabel = paymentLabel;
        }
        public String label() { return label; }
        public String bookingLabel() { return bookingLabel; }
        public String paymentLabel() { return paymentLabel; }
    }

    record Entry(String reference, String guestName, String guestEmail, String roomType,
                 LocalDate arrival, LocalDate departure, int rooms, Long totalMinor,
                 String discountLabel, State state) {
        public String totalLabel() {
            if (totalMinor == null) return "待预订服务报价";
            NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
            format.setMinimumFractionDigits(2); format.setMaximumFractionDigits(2);
            return "CNY " + format.format(BigDecimal.valueOf(totalMinor, 2));
        }
        public long nights() { return java.time.temporal.ChronoUnit.DAYS.between(arrival, departure); }
    }
}
