package com.holidayresort.booking.web.account;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/** B-owned page projections. Preview commands do not authenticate or create A's accounts. */
public interface AccountPageGateway {
    Optional<Profile> profileFor(String viewerId);
    void previewSignIn(String viewerId, String email);
    void previewRegistration(String viewerId, String displayName, String email);
    Optional<Profile> registrationFor(String viewerId);
    void clearPreview(String viewerId);
    Membership membershipExample(String example);

    record Profile(String displayName, String email) { }

    enum MemberDisplayState {
        NONE("未开通"), ACTIVE("有效"), EXPIRED("已过期"), PENDING("待确认");
        private final String label;
        MemberDisplayState(String label) { this.label = label; }
        public String label() { return label; }
    }

    record Membership(MemberDisplayState state, LocalDate startDate, LocalDate endDate,
                      Long feeMinor, String discountLabel, String validityLabel) {
        public String feeLabel() {
            if (feeMinor == null) return "待会员服务报价";
            NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
            format.setMinimumFractionDigits(2);
            format.setMaximumFractionDigits(2);
            return "CNY " + format.format(BigDecimal.valueOf(feeMinor, 2));
        }
    }
}
