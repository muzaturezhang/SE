package com.holidayresort.booking.web.stay;

import com.holidayresort.booking.booking.application.BookingException;
import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.payment.application.PaymentException;
import com.holidayresort.booking.payment.application.PaymentService;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import com.holidayresort.booking.room.application.InvalidRoomSearchException;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.domain.RoomType;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Authenticated customer booking flow backed by the real booking, inventory and payment services. */
@Controller
@RequestMapping("/stays")
public class CustomerStayController {

    private final BookingService bookings;
    private final PaymentService payments;
    private final RoomQueryService rooms;
    private final MemberRepository members;
    private final Clock clock;

    public CustomerStayController(BookingService bookings, PaymentService payments,
            RoomQueryService rooms, MemberRepository members, Clock clock) {
        this.bookings = bookings;
        this.payments = payments;
        this.rooms = rooms;
        this.members = members;
        this.clock = clock;
    }

    @InitBinder("stayForm")
    void stayFields(WebDataBinder binder) {
        binder.setAllowedFields("roomType", "arrival", "departure", "rooms", "guestName", "guestEmail");
    }

    @InitBinder("changeForm")
    void changeFields(WebDataBinder binder) {
        binder.setAllowedFields("roomType", "arrival", "departure", "rooms");
    }

    @ModelAttribute("today")
    LocalDate today() {
        return LocalDate.now(clock);
    }

    @ModelAttribute("roomTypes")
    List<RoomType> roomTypes() {
        return rooms.listRoomTypes();
    }

    @GetMapping("/new")
    String newBooking(Authentication authentication, Model model, HttpServletResponse response,
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) LocalDate arrival,
            @RequestParam(required = false) LocalDate departure,
            @RequestParam(required = false) Integer roomCount) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        List<RoomType> availableTypes = rooms.listRoomTypes();
        if (availableTypes.isEmpty()) {
            throw new StayUiException(503, "暂无可预订房型，请稍后再试。");
        }
        StayForm form = new StayForm();
        form.setRoomType(roomType == null || roomType.equals("all")
                ? availableTypes.get(0).id() : roomType);
        form.setArrival(arrival == null ? LocalDate.now(clock).plusDays(10) : arrival);
        form.setDeparture(departure == null ? LocalDate.now(clock).plusDays(12) : departure);
        form.setRooms(roomCount == null || roomCount < 1 ? 1 : roomCount);
        form.setGuestName(customer.displayName());
        form.setGuestEmail(customer.email());
        model.addAttribute("stayForm", form);
        return "stay/customer-new";
    }

    @PostMapping("/confirm")
    String confirm(@Valid @ModelAttribute("stayForm") StayForm form, BindingResult errors,
            Authentication authentication, Model model, HttpServletResponse response) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        if (errors.hasErrors()) {
            response.setStatus(400);
            addErrors(model, errors);
            return "stay/customer-new";
        }
        try {
            BookingService.Quote quote = bookings.quote(customer.id(), form.getRoomType(),
                    form.getArrival(), form.getDeparture(), form.getRooms());
            addQuote(model, quote);
            model.addAttribute("idempotencyKey", UUID.randomUUID().toString());
            return "stay/customer-confirm";
        } catch (BookingException | InvalidRoomSearchException exception) {
            response.setStatus(400);
            addError(model, readable(exception));
            return "stay/customer-new";
        }
    }

    @PostMapping
    String create(@Valid @ModelAttribute("stayForm") StayForm form, BindingResult errors,
            @RequestParam(required = false) String idempotencyKey,
            Authentication authentication, Model model, HttpServletResponse response,
            RedirectAttributes redirect) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        if (errors.hasErrors()) {
            response.setStatus(400);
            addErrors(model, errors);
            addQuoteIfPossible(customer, form, model);
            model.addAttribute("idempotencyKey", idempotencyKey);
            return "stay/customer-confirm";
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            response.setStatus(400);
            addError(model, "确认信息已失效，请返回上一步重新查看报价。");
            addQuote(model, bookings.quote(customer.id(), form.getRoomType(),
                    form.getArrival(), form.getDeparture(), form.getRooms()));
            return "stay/customer-confirm";
        }
        try {
            Booking booking = bookings.createBooking(customer.id(), form.getRoomType(),
                    form.getArrival(), form.getDeparture(), form.getRooms(),
                    form.getGuestName(), form.getGuestEmail(), idempotencyKey);
            redirect.addFlashAttribute("stayMessage", "预订已创建，请在支付截止时间前完成支付。");
            return "redirect:/stays/" + booking.reference();
        } catch (BookingException | InvalidRoomSearchException exception) {
            response.setStatus(409);
            addError(model, readable(exception));
            addQuoteIfPossible(customer, form, model);
            model.addAttribute("idempotencyKey", idempotencyKey);
            return "stay/customer-confirm";
        }
    }

    @GetMapping
    String list(Authentication authentication, Model model, HttpServletResponse response) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        model.addAttribute("reservations", bookings.listByCustomer(customer.id()).stream()
                .map(booking -> reservationView(booking, paymentOrNull(booking.reference())))
                .toList());
        return "stay/customer-index";
    }

    @GetMapping("/{reference}")
    String detail(@PathVariable String reference, Authentication authentication, Model model,
            HttpServletResponse response) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        Booking booking = bookings.findForCustomer(customer.id(), reference);
        Payment payment = paymentOrNull(reference);
        RoomType roomType = roomType(booking.roomTypeId());
        model.addAttribute("booking", booking);
        model.addAttribute("payment", payment);
        model.addAttribute("roomTypeName", roomTypeName(roomType, booking.roomTypeId()));
        model.addAttribute("imagePath", roomType == null ? "/images/coastal-retreat.jpg" : roomType.imagePath());
        model.addAttribute("imageAlt", roomType == null ? "Holiday Resort 度假居所" : roomType.imageAlt());
        model.addAttribute("totalLabel", money(booking.price().totalMinor()));
        model.addAttribute("subtotalLabel", money(booking.price().subtotalMinor()));
        model.addAttribute("discountAmountLabel", money(
                booking.price().subtotalMinor() - booking.price().totalMinor()));
        model.addAttribute("bookingStatusLabel", bookingStatusLabel(booking.status()));
        model.addAttribute("paymentStatusLabel", paymentStatusLabel(payment));
        return "stay/customer-detail";
    }

    @GetMapping("/{reference}/change")
    String changeForm(@PathVariable String reference, Authentication authentication, Model model,
            HttpServletResponse response) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        Booking booking = bookings.findForCustomer(customer.id(), reference);
        StayChangeForm form = new StayChangeForm();
        form.setRoomType(booking.roomTypeId());
        form.setArrival(booking.stay().arrival());
        form.setDeparture(booking.stay().departure());
        form.setRooms(booking.roomCount());
        model.addAttribute("booking", booking);
        model.addAttribute("changeForm", form);
        model.addAttribute("roomTypeName", roomTypeName(roomType(booking.roomTypeId()), booking.roomTypeId()));
        return "stay/customer-change";
    }

    @PostMapping("/{reference}/change")
    String change(@PathVariable String reference, @Valid @ModelAttribute("changeForm") StayChangeForm form,
            BindingResult errors, Authentication authentication, Model model,
            HttpServletResponse response, RedirectAttributes redirect) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        Booking booking = bookings.findForCustomer(customer.id(), reference);
        if (errors.hasErrors()) {
            response.setStatus(400);
            addErrors(model, errors);
            model.addAttribute("booking", booking);
            return "stay/customer-change";
        }
        try {
            bookings.change(customer.id(), reference, form.getRoomType(), form.getArrival(),
                    form.getDeparture(), form.getRooms());
            redirect.addFlashAttribute("stayMessage", "预订已改期并重新报价。");
            return "redirect:/stays/" + reference;
        } catch (BookingException | InvalidRoomSearchException exception) {
            response.setStatus(409);
            addError(model, readable(exception));
            model.addAttribute("booking", booking);
            return "stay/customer-change";
        }
    }

    @PostMapping("/{reference}/cancel")
    String cancel(@PathVariable String reference, Authentication authentication,
            HttpServletResponse response, RedirectAttributes redirect) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        bookings.findForCustomer(customer.id(), reference);
        try {
            bookings.cancel(customer.id(), reference);
            redirect.addFlashAttribute("stayMessage", "预订已取消，房间已释放。");
        } catch (BookingException exception) {
            redirect.addFlashAttribute("stayError", readable(exception));
        }
        return "redirect:/stays/" + reference;
    }

    @PostMapping("/{reference}/pay")
    String pay(@PathVariable String reference, Authentication authentication,
            HttpServletResponse response, RedirectAttributes redirect) {
        noStore(response);
        Customer customer = requireCustomer(authentication);
        bookings.findForCustomer(customer.id(), reference);
        try {
            Payment payment = payments.initiatePayment(reference, customer.id());
            return "redirect:/sandbox/payments/" + payment.providerReference();
        } catch (PaymentException | BookingException exception) {
            redirect.addFlashAttribute("stayError", readable(exception));
            return "redirect:/stays/" + reference;
        }
    }

    @ExceptionHandler(StayUiException.class)
    String stayError(StayUiException exception, HttpServletResponse response, Model model) {
        response.setStatus(exception.status());
        noStore(response);
        addError(model, exception.getMessage());
        return "stay/customer-error";
    }

    @ExceptionHandler(BookingException.class)
    String bookingError(BookingException exception, HttpServletResponse response, Model model) {
        response.setStatus(bookingStatus(exception));
        noStore(response);
        addError(model, readable(exception));
        return "stay/customer-error";
    }

    @ExceptionHandler(PaymentException.class)
    String paymentError(PaymentException exception, HttpServletResponse response, Model model) {
        response.setStatus(paymentStatus(exception));
        noStore(response);
        addError(model, readable(exception));
        return "stay/customer-error";
    }

    private ReservationView reservationView(Booking booking, Payment payment) {
        RoomType roomType = roomType(booking.roomTypeId());
        return new ReservationView(booking, payment,
                roomTypeName(roomType, booking.roomTypeId()),
                roomType == null ? "/images/coastal-retreat.jpg" : roomType.imagePath(),
                roomType == null ? "Holiday Resort 度假居所" : roomType.imageAlt(),
                money(booking.price().totalMinor()),
                bookingStatusLabel(booking.status()),
                paymentStatusLabel(payment));
    }

    private RoomType roomType(String roomTypeId) {
        return rooms.listRoomTypes().stream()
                .filter(type -> type.id().equals(roomTypeId))
                .findFirst().orElse(null);
    }

    private static String roomTypeName(RoomType roomType, String fallback) {
        return roomType == null ? fallback : roomType.name();
    }

    private void addQuoteIfPossible(Customer customer, StayForm form, Model model) {
        try {
            addQuote(model, bookings.quote(customer.id(), form.getRoomType(),
                    form.getArrival(), form.getDeparture(), form.getRooms()));
        } catch (RuntimeException ignored) {
            // The validation or conflict message remains the useful result.
        }
    }

    private static void addQuote(Model model, BookingService.Quote quote) {
        model.addAttribute("quote", quote);
        model.addAttribute("quoteTotalLabel", money(quote.price().totalMinor()));
        model.addAttribute("quoteRateLabel", money(quote.price().nightlyRateMinor()));
    }

    private Payment paymentOrNull(String reference) {
        try {
            return payments.findByBookingReference(reference);
        } catch (PaymentException exception) {
            return null;
        }
    }

    private Customer requireCustomer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new StayUiException(401, "请先登录后访问预订页面。");
        }
        return members.findById(authentication.getName())
                .orElseThrow(() -> new StayUiException(404, "当前账户不存在，请重新登录。"));
    }

    private static void addError(Model model, String message) {
        model.addAttribute("formErrors", List.of(message));
    }

    private static void addErrors(Model model, BindingResult errors) {
        model.addAttribute("formErrors", errors.getAllErrors().stream()
                .map(error -> error instanceof FieldError field && field.isBindingFailure()
                        ? "日期须为有效的年-月-日，房间数量须为正整数。"
                        : error.getDefaultMessage() == null ? "输入有误。" : error.getDefaultMessage())
                .distinct().toList());
    }

    private static String readable(RuntimeException exception) {
        return exception.getMessage() == null ? "请求无法完成。" : exception.getMessage();
    }

    private static int bookingStatus(BookingException exception) {
        return switch (exception.kind()) {
            case NOT_FOUND -> 404;
            case INVALID -> 400;
            case CONFLICT -> 409;
        };
    }

    private static int paymentStatus(PaymentException exception) {
        return switch (exception.kind()) {
            case NOT_FOUND -> 404;
            case INVALID -> 400;
            case CONFLICT, PROVIDER_ERROR -> 409;
        };
    }

    private static String bookingStatusLabel(BookingStatus status) {
        return switch (status) {
            case PENDING -> "待支付";
            case PAID -> "已支付";
            case FAILED -> "支付失败";
            case UNDER_REVIEW -> "支付待核对";
            case EXPIRED -> "已过期";
            case CANCELLED -> "已取消";
        };
    }

    private static String paymentStatusLabel(Payment payment) {
        if (payment == null) {
            return "尚未发起支付";
        }
        return switch (payment.status()) {
            case NEW, PENDING -> "支付处理中";
            case PAID -> "已支付";
            case FAILED -> "支付失败";
            case UNDER_REVIEW -> "待人工核对";
            case EXPIRED -> "支付已过期";
        };
    }

    private static String money(long minor) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "CNY " + format.format(BigDecimal.valueOf(minor, 2));
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    public record ReservationView(Booking booking, Payment payment, String roomTypeName,
            String imagePath, String imageAlt, String totalLabel,
            String bookingStatusLabel, String paymentStatusLabel) {
    }
}