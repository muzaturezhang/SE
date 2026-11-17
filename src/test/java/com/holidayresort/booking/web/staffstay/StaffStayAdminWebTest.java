package com.holidayresort.booking.web.staffstay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.member.application.RegistrationService;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.payment.application.PaymentService;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.infrastructure.SandboxPaymentProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:staff-booking-admin-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(StaffStayAdminWebTest.FixedTime.class)
class StaffStayAdminWebTest {

    @Autowired MockMvc mvc;
    @Autowired BookingService bookings;
    @Autowired PaymentService payments;
    @Autowired SandboxPaymentProvider provider;
    @Autowired RegistrationService registration;
    private Customer customer;
    private final MockHttpSession staffSession = new MockHttpSession();

    @BeforeEach
    void registerCustomer() {
        customer = registration.register("员工页测试客户", "staff-admin-" + UUID.randomUUID() + "@example.invalid",
                "safePass1");
    }

    @Test
    void bookingAdminPagesRequireStaffRoleAndDoNotCache() throws Exception {
        mvc.perform(get("/admin/stays")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/stays").with(user(customer.id()).roles("CUSTOMER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/stays").with(user("front-desk").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("预订管理")))
                .andExpect(content().string(containsString("STAFF 权限页面")))
                .andExpect(content().string(not(containsString("本地 UI 演示"))))
                .andExpect(content().string(containsString("href=\"/admin/stays\"")));
    }

    @Test
    void staffCanFilterByReferenceArrivalAndBookingStateWithReadableValidation() throws Exception {
        Booking booking = createBooking("2027-02-01", "2027-02-03", "庭院测试住客");
        mvc.perform(get("/admin/stays").with(user("front-desk").roles("STAFF"))
                        .param("reference", booking.reference().substring(5, 12))
                        .param("arrival", "2027-02-01")
                        .param("state", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(booking.reference())))
                .andExpect(content().string(containsString("待支付")))
                .andExpect(content().string(containsString("尚未发起支付")));

        mvc.perform(get("/admin/stays").with(user("front-desk").roles("STAFF"))
                        .param("reference", "A".repeat(41)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("最多 40 位")));
        mvc.perform(get("/admin/stays").with(user("front-desk").roles("STAFF"))
                        .param("arrival", "2027-02-30"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("入住日期须为有效")));
        mvc.perform(get("/admin/stays").with(user("front-desk").roles("STAFF"))
                        .param("state", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("请选择列表中的预订状态")));
        mvc.perform(get("/admin/stays").with(user("front-desk").roles("STAFF")).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("页码不能小于 0")));
    }

    @Test
    void staffListPaginatesAfterTwentyRecords() throws Exception {
        LocalDate first = LocalDate.of(2027, 4, 1);
        for (int index = 0; index < 21; index++) {
            LocalDate arrival = first.plusDays(index * 3L);
            createBooking(arrival.toString(), arrival.plusDays(2).toString(), "分页测试住客");
        }
        mvc.perform(get("/admin/stays").with(user("front-desk").roles("STAFF")).param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("下一页")))
                .andExpect(content().string(containsString("第 1 /")));
        mvc.perform(get("/admin/stays").with(user("front-desk").roles("STAFF")).param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("上一页")));
    }

    @Test
    void detailShowsAllocatedRoomsAndStaffCanConfirmUnknownPayment() throws Exception {
        Booking booking = createBooking("2027-08-01", "2027-08-03", "<script>alert(1)</script>");
        Payment payment = markUnderReview(booking);
        String detailUrl = "/admin/stays/" + booking.reference() + "?referenceFilter=" + booking.reference()
                + "&stateFilter=UNDER_REVIEW";

        mvc.perform(get(detailUrl).with(user("front-desk").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString(booking.reference())))
                .andExpect(content().string(containsString(booking.allocatedRoomIds().get(0))))
                .andExpect(content().string(containsString("待人工核对")))
                .andExpect(content().string(containsString("确认支付成功")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));

        mvc.perform(post("/admin/stays/" + booking.reference() + "/payment/confirm")
                        .with(user("front-desk").roles("STAFF")))
                .andExpect(status().isForbidden());
        assertThat(payments.findByProviderReference(payment.providerReference()).status().name())
                .isEqualTo("UNDER_REVIEW");

        var confirmed = mvc.perform(post("/admin/stays/" + booking.reference() + "/payment/confirm")
                        .session(staffSession).with(user("front-desk").roles("STAFF")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/stays/" + booking.reference() + "*"))
                .andReturn();
        assertThat(bookings.findByReference(booking.reference()).status()).isEqualTo(BookingStatus.PAID);
        assertThat(payments.findByProviderReference(payment.providerReference()).status().name()).isEqualTo("PAID");
        mvc.perform(get(confirmed.getResponse().getRedirectedUrl()).session(staffSession)
                        .with(user("front-desk").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("支付结果已确认")))
                .andExpect(content().string(containsString("已支付")));
    }


    @Test
    void staffCanRejectUnknownPaymentWithAuditableReason() throws Exception {
        Booking booking = createBooking("2027-09-01", "2027-09-03", "拒绝测试住客");
        Payment payment = markUnderReview(booking);
        String url = "/admin/stays/" + booking.reference() + "/payment/reject";

        var invalidReason = mvc.perform(post(url).session(staffSession)
                        .with(user("front-desk").roles("STAFF")).with(csrf()).param("reason", "x"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(bookings.findByReference(booking.reference()).status()).isEqualTo(BookingStatus.UNDER_REVIEW);
        mvc.perform(get(invalidReason.getResponse().getRedirectedUrl()).session(staffSession)
                        .with(user("front-desk").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("拒绝原因须为 3–255 个字符")));

        var rejected = mvc.perform(post(url).session(staffSession)
                        .with(user("front-desk").roles("STAFF")).with(csrf())
                        .param("reason", "支付服务查询显示交易关闭，且未发生扣款。"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(bookings.findByReference(booking.reference()).status()).isEqualTo(BookingStatus.FAILED);
        assertThat(payments.findByProviderReference(payment.providerReference()).status().name()).isEqualTo("FAILED");
        mvc.perform(get(rejected.getResponse().getRedirectedUrl()).session(staffSession)
                        .with(user("front-desk").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("支付结果已拒绝")))
                .andExpect(content().string(containsString("支付失败")));
    }


    @Test
    void pendingBookingWithoutPaymentHasNoReconciliationAction() throws Exception {
        Booking booking = createBooking("2027-10-01", "2027-10-03", "无支付住客");
        mvc.perform(get("/admin/stays/" + booking.reference()).with(user("front-desk").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("尚未发起支付")))
                .andExpect(content().string(containsString("当前记录没有待核对的支付结果")))
                .andExpect(content().string(not(containsString("确认支付成功"))));
    }

    @Test
    void missingRecordAndInvalidFilterErrorsAreReadable() throws Exception {
        Booking booking = createBooking("2027-11-01", "2027-11-03", "错误页测试住客");
        mvc.perform(get("/admin/stays/BOOK-NOT-FOUND").with(user("front-desk").roles("STAFF")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("预订记录未能显示")))
                .andExpect(content().string(containsString("Booking not found")));
        mvc.perform(get("/admin/stays/" + booking.reference()).with(user("front-desk").roles("STAFF"))
                        .param("arrivalFilter", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("入住日期须为有效")));
        mvc.perform(get("/admin/stays/" + booking.reference()).with(user("front-desk").roles("STAFF"))
                        .param("stateFilter", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("请选择列表中的预订状态")));
    }

    @Test
    void paymentActionWithoutPaymentReturnsReadableErrorWithoutChangingBooking() throws Exception {
        Booking booking = createBooking("2027-12-01", "2027-12-03", "无支付操作住客");
        var result = mvc.perform(post("/admin/stays/" + booking.reference() + "/payment/confirm")
                        .session(staffSession).with(user("front-desk").roles("STAFF")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(bookings.findByReference(booking.reference()).status()).isEqualTo(BookingStatus.PENDING);
        mvc.perform(get(result.getResponse().getRedirectedUrl()).session(staffSession)
                        .with(user("front-desk").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("没有可核对的支付记录")));
    }

    private Booking createBooking(String arrival, String departure, String guestName) {
        return bookings.createBooking(customer.id(), "courtyard", LocalDate.parse(arrival),
                LocalDate.parse(departure), 1, guestName, customer.email(),
                "staff-test-" + UUID.randomUUID());
    }

    private Payment markUnderReview(Booking booking) {
        Payment payment = payments.initiatePayment(booking.reference(), customer.id());
        provider.simulateUnknown(payment.providerReference());
        return payments.handleCallback(payment.providerReference());
    }

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock staffAdminClock() {
            return Clock.fixed(Instant.parse("2026-11-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
