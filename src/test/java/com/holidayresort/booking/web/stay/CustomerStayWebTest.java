package com.holidayresort.booking.web.stay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.member.application.port.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:customer-booking-ui-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(CustomerStayWebTest.FixedTime.class)
class CustomerStayWebTest {

    @Autowired MockMvc mvc;
    @Autowired BookingService bookings;
    @Autowired MemberRepository members;

    @Test
    void customerBookingPagesRequireCustomerRoleAndDoNotCache() throws Exception {
        mvc.perform(get("/stays")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/account/login"));
        mvc.perform(get("/stays/new")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/account/login"));
        mvc.perform(get("/stays/new").with(user("staff").roles("STAFF")))
                .andExpect(status().isForbidden());

        var customer = customerSession("access-check");
        mvc.perform(get("/stays/new").session(customer.session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("安排入住")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("本地 UI 演示"))))
                .andExpect(content().string(not(containsString("仅验证页面"))));
    }

    @Test
    void confirmationUsesServerQuoteAndDuplicateSubmitReusesOneBooking() throws Exception {
        var customer = customerSession("quote-idempotency");
        var fields = stayFields("2026-11-20", "2026-11-22", "courtyard", "1",
                "林晓", customer.email());
        var confirmed = mvc.perform(formPost("/stays/confirm", customer.session, fields)
                        .param("totalMinor", "1").param("state", "PAID").param("customerId", "forged"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(view().name("stay/customer-confirm"))
                .andExpect(model().attributeExists("quote", "idempotencyKey"))
                .andExpect(content().string(containsString("创建待支付预订")))
                .andReturn();
        BookingService.Quote quote = (BookingService.Quote) confirmed.getModelAndView().getModel().get("quote");
        assertThat(quote.price().totalMinor()).isEqualTo(196000);
        assertThat(quote.price().discountPercent()).isZero();
        String idempotencyKey = (String) confirmed.getModelAndView().getModel().get("idempotencyKey");

        var created = mvc.perform(formPost("/stays", customer.session, fields)
                        .param("idempotencyKey", idempotencyKey))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/stays/BOOK-*"))
                .andReturn();
        String reference = created.getResponse().getRedirectedUrl().substring("/stays/".length());
        String customerId = members.findByEmail(customer.email()).orElseThrow().id();
        assertThat(bookings.listByCustomer(customerId)).hasSize(1);

        mvc.perform(formPost("/stays", customer.session, fields).param("idempotencyKey", idempotencyKey))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/stays/" + reference));
        assertThat(bookings.listByCustomer(customerId)).hasSize(1);
        assertThat(bookings.findByReference(reference).status()).isEqualTo(BookingStatus.PENDING);

        mvc.perform(get("/stays/" + reference).session(customer.session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString(reference)))
                .andExpect(content().string(containsString("待支付")))
                .andExpect(content().string(containsString("尚未发起支付")))
                .andExpect(content().string(containsString("CNY 1,960.00")))
                .andExpect(content().string(not(containsString("safePass1"))));
    }

    @Test
    void invalidDatesAndMissingCsrfAreReadableWithoutCreatingBookings() throws Exception {
        var customer = customerSession("validation");
        var invalid = stayFields("2026-11-20", "2026-11-20", "courtyard", "1",
                "林晓", customer.email());
        mvc.perform(formPost("/stays/confirm", customer.session, invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("退房日期必须晚于入住日期")))
                .andExpect(content().string(not(containsString("Failed to convert"))));

        var valid = stayFields("2026-11-21", "2026-11-23", "courtyard", "1",
                "林晓", customer.email());
        mvc.perform(post("/stays/confirm").session(customer.session)
                        .param("roomType", valid.get("roomType"))
                        .param("arrival", valid.get("arrival"))
                        .param("departure", valid.get("departure"))
                        .param("rooms", valid.get("rooms"))
                        .param("guestName", valid.get("guestName"))
                        .param("guestEmail", valid.get("guestEmail")))
                .andExpect(status().isForbidden());
        String customerId = members.findByEmail(customer.email()).orElseThrow().id();
        assertThat(bookings.listByCustomer(customerId)).isEmpty();
    }

    @Test
    void customersOnlySeeTheirOwnBookingsAndCannotOperateForeignReferences() throws Exception {
        var owner = customerSession("owner-isolation");
        var other = customerSession("other-isolation");
        String reference = createBooking(owner.session, "2026-11-25", "2026-11-27",
                "courtyard", "1", owner.email());

        mvc.perform(get("/stays").session(owner.session))
                .andExpect(status().isOk()).andExpect(content().string(containsString(reference)));
        mvc.perform(get("/stays").session(other.session))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(reference))));

        mvc.perform(get("/stays/" + reference).session(other.session)).andExpect(status().isNotFound());
        mvc.perform(get("/stays/" + reference + "/change").session(other.session)).andExpect(status().isNotFound());
        mvc.perform(post("/stays/" + reference + "/pay").session(other.session).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/stays/" + reference + "/cancel").session(other.session).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(bookings.findByReference(reference).status()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void customerCanChangeThenCancelAndInventoryIsReleased() throws Exception {
        var customer = customerSession("change-cancel");
        String reference = createBooking(customer.session, "2026-12-10", "2026-12-12",
                "courtyard", "1", customer.email());

        var changed = stayFields("2026-12-15", "2026-12-17", "courtyard", "2",
                "林晓", customer.email());
        mvc.perform(formPost("/stays/" + reference + "/change", customer.session, changed))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/stays/" + reference));
        mvc.perform(get("/stays/" + reference).session(customer.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-12-15")))
                .andExpect(content().string(containsString("预订已改期并重新报价")));
        var changedBooking = bookings.findByReference(reference);
        assertThat(changedBooking.roomCount()).isEqualTo(2);
        assertThat(changedBooking.stay().arrival().toString()).isEqualTo("2026-12-15");
        assertThat(changedBooking.price().totalMinor()).isEqualTo(392000);

        mvc.perform(post("/stays/" + reference + "/cancel").session(customer.session).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/stays/" + reference));
        assertThat(bookings.findByReference(reference).status()).isEqualTo(BookingStatus.CANCELLED);
        mvc.perform(get("/stays/" + reference).session(customer.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("已取消")))
                .andExpect(content().string(containsString("房间已释放")));
    }

    @Test
    void bookingPaymentReturnsToOwnedBookingAndCompletesWithoutCredentialInput() throws Exception {
        var owner = customerSession("booking-payment-owner");
        var other = customerSession("booking-payment-other");
        String reference = createBooking(owner.session, "2027-01-05", "2027-01-07",
                "courtyard", "1", owner.email());

        var paymentRedirect = mvc.perform(post("/stays/" + reference + "/pay")
                        .session(owner.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/sandbox/payments/*"))
                .andReturn();
        String paymentUrl = paymentRedirect.getResponse().getRedirectedUrl();

        mvc.perform(get(paymentUrl).session(other.session)).andExpect(status().isNotFound());
        mvc.perform(get(paymentUrl).session(owner.session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("住宿预订")))
                .andExpect(content().string(containsString("CNY 1,960.00")))
                .andExpect(content().string(not(containsString("cardNumber"))))
                .andExpect(content().string(not(containsString("password"))));

        mvc.perform(post(paymentUrl + "/success").session(owner.session).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/stays/" + reference));
        assertThat(bookings.findByReference(reference).status()).isEqualTo(BookingStatus.PAID);
        mvc.perform(get("/stays/" + reference).session(owner.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("支付成功，预订已确认")))
                .andExpect(content().string(containsString("已支付")))
                .andExpect(content().string(not(containsString("继续支付"))));
    }

    @Test
    void createValidationAndInvalidQuoteAreReadableWithoutCreatingBookings() throws Exception {
        var customer = customerSession("create-validation");
        var invalidType = stayFields("2027-04-01", "2027-04-03", "missing", "1",
                "林晓", customer.email());
        mvc.perform(formPost("/stays/confirm", customer.session, invalidType))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("stay/customer-new"))
                .andExpect(content().string(containsString("找不到所选房型")));

        var fields = stayFields("2027-04-05", "2027-04-07", "courtyard", "1",
                "林晓", customer.email());
        MvcResult confirmed = mvc.perform(formPost("/stays/confirm", customer.session, fields))
                .andExpect(status().isOk()).andReturn();
        String key = (String) confirmed.getModelAndView().getModel().get("idempotencyKey");
        var missingEmail = new HashMap<>(fields);
        missingEmail.remove("guestEmail");
        mvc.perform(formPost("/stays", customer.session, missingEmail).param("idempotencyKey", key))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("stay/customer-confirm"))
                .andExpect(content().string(containsString("暂时无法创建预订")))
                .andExpect(content().string(containsString("联系邮箱")));

        mvc.perform(formPost("/stays", customer.session, fields))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("确认信息已失效")));
        String customerId = members.findByEmail(customer.email()).orElseThrow().id();
        assertThat(bookings.listByCustomer(customerId)).isEmpty();
    }

    @Test
    void changePageRendersForEditableBookingAndExplainsTerminalState() throws Exception {
        var customer = customerSession("change-page");
        String reference = createBooking(customer.session, "2027-05-10", "2027-05-12",
                "courtyard", "1", customer.email());
        mvc.perform(get("/stays/" + reference + "/change").session(customer.session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("调整入住安排")))
                .andExpect(content().string(containsString("庭院客房")))
                .andExpect(content().string(containsString("name=\"arrival\"")));

        mvc.perform(post("/stays/" + reference + "/cancel").session(customer.session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/stays/" + reference + "/change").session(customer.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("当前预订不能改期")))
                .andExpect(content().string(not(containsString("name=\"arrival\""))));
    }

    @Test
    void failedAndUnknownSandboxPaymentsReturnToBookingWithSeparateStates() throws Exception {
        var customer = customerSession("payment-states");
        String failedReference = createBooking(customer.session, "2027-06-01", "2027-06-03",
                "courtyard", "1", customer.email());
        String failedUrl = startPayment(customer.session, failedReference);
        mvc.perform(post(failedUrl + "/failed").session(customer.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/stays/" + failedReference));
        assertThat(bookings.findByReference(failedReference).status()).isEqualTo(BookingStatus.FAILED);
        mvc.perform(get("/stays/" + failedReference).session(customer.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("支付失败")));

        String reviewReference = createBooking(customer.session, "2027-07-01", "2027-07-03",
                "courtyard", "1", customer.email());
        String reviewUrl = startPayment(customer.session, reviewReference);
        mvc.perform(post(reviewUrl + "/unknown").session(customer.session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/stays/" + reviewReference));
        assertThat(bookings.findByReference(reviewReference).status()).isEqualTo(BookingStatus.UNDER_REVIEW);
        mvc.perform(get("/stays/" + reviewReference).session(customer.session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("支付待核对")))
                .andExpect(content().string(containsString("待人工核对")));
    }

    private String startPayment(MockHttpSession session, String reference) throws Exception {
        return mvc.perform(post("/stays/" + reference + "/pay").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/sandbox/payments/*"))
                .andReturn().getResponse().getRedirectedUrl();
    }

    private String createBooking(MockHttpSession session, String arrival, String departure,
            String roomType, String rooms, String email) throws Exception {
        var fields = stayFields(arrival, departure, roomType, rooms, "测试住客", email);
        MvcResult confirmed = mvc.perform(formPost("/stays/confirm", session, fields))
                .andExpect(status().isOk())
                .andExpect(view().name("stay/customer-confirm"))
                .andReturn();
        String idempotencyKey = (String) confirmed.getModelAndView().getModel().get("idempotencyKey");
        MvcResult created = mvc.perform(formPost("/stays", session, fields)
                        .param("idempotencyKey", idempotencyKey))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/stays/BOOK-*"))
                .andReturn();
        String reference = created.getResponse().getRedirectedUrl().substring("/stays/".length());
        mvc.perform(get("/stays/" + reference).session(session))
                .andExpect(status().isOk());
        return reference;
    }

    private Map<String, String> stayFields(String arrival, String departure, String roomType,
            String rooms, String guestName, String guestEmail) {
        return new HashMap<>(Map.of(
                "roomType", roomType,
                "arrival", arrival,
                "departure", departure,
                "rooms", rooms,
                "guestName", guestName,
                "guestEmail", guestEmail));
    }

    private MockHttpServletRequestBuilder formPost(String path, MockHttpSession session,
            Map<String, String> fields) {
        var request = post(path).session(session).with(csrf());
        fields.forEach(request::param);
        return request;
    }

    private CustomerSession customerSession(String label) throws Exception {
        String email = label + "-" + UUID.randomUUID() + "@example.invalid";
        mvc.perform(post("/account/register").with(csrf())
                        .param("displayName", "测试客户")
                        .param("email", email)
                        .param("password", "safePass1")
                        .param("confirmPassword", "safePass1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/login"));
        MvcResult login = mvc.perform(post("/account/login").with(csrf())
                        .param("username", email)
                        .param("password", "safePass1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"))
                .andReturn();
        return new CustomerSession(email, (MockHttpSession) login.getRequest().getSession(false));
    }

    private record CustomerSession(String email, MockHttpSession session) {
    }

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock customerStayClock() {
            return Clock.fixed(Instant.parse("2026-11-10T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        }
    }
}
