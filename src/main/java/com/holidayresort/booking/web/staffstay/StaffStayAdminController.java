package com.holidayresort.booking.web.staffstay;

import com.holidayresort.booking.booking.application.BookingException;
import com.holidayresort.booking.booking.application.BookingService;
import com.holidayresort.booking.booking.domain.Booking;
import com.holidayresort.booking.booking.domain.BookingStatus;
import com.holidayresort.booking.payment.application.PaymentException;
import com.holidayresort.booking.payment.application.PaymentService;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentPurpose;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.room.domain.RoomType;
import com.holidayresort.booking.web.StaffNavigationAdvice;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/** Real STAFF booking review routes. The B-owned preview controller is active only in the preview profile. */
@Controller
@Profile("!staff-booking-ui")
@RequestMapping("/admin/stays")
public class StaffStayAdminController {

    private static final int PAGE_SIZE = 20;
    private final BookingService bookings;
    private final PaymentService payments;
    private final RoomQueryService rooms;
    private final StaffNavigationAdvice navigation;

    public StaffStayAdminController(BookingService bookings, PaymentService payments, RoomQueryService rooms,
            StaffNavigationAdvice navigation) {
        this.bookings = bookings;
        this.payments = payments;
        this.rooms = rooms;
        this.navigation = navigation;
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "") String reference,
            @RequestParam(defaultValue = "") String arrival,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            HttpServletResponse response, Model model) {
        noStore(response);
        model.addAttribute("staffSection", "stays");
        model.addAttribute("stateOptions", stateOptions());
        try {
            Filters filters = filters(reference, arrival, state, page);
            List<Booking> matching = bookings.listForStaff().stream()
                    .filter(booking -> filters.reference().isEmpty()
                            || booking.reference().contains(filters.reference()))
                    .filter(booking -> filters.arrival() == null
                            || booking.stay().arrival().equals(filters.arrival()))
                    .filter(booking -> filters.state() == null || booking.status() == filters.state())
                    .sorted(Comparator.comparing(Booking::createdAt).reversed())
                    .toList();
            int pageCount = Math.max(1, (matching.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            int currentPage = Math.min(filters.page(), pageCount - 1);
            int from = Math.min(currentPage * PAGE_SIZE, matching.size());
            int to = Math.min(from + PAGE_SIZE, matching.size());
            model.addAttribute("entries", matching.subList(from, to).stream()
                    .map(booking -> view(booking, paymentOrNull(booking.reference())))
                    .toList());
            addNavigation(model, filters, currentPage, pageCount, matching.size());
        } catch (StaffStayUiException exception) {
            response.setStatus(exception.status());
            model.addAttribute("filterError", exception.getMessage());
            model.addAttribute("entries", List.of());
            addNavigation(model, safeFilters(reference, arrival, state, page), 0, 1, 0);
        }
        return "staffstay/admin-index";
    }

    @GetMapping("/{reference}")
    String detail(@PathVariable String reference,
            @RequestParam(defaultValue = "") String referenceFilter,
            @RequestParam(defaultValue = "") String arrivalFilter,
            @RequestParam(required = false) String stateFilter,
            @RequestParam(defaultValue = "0") int page,
            HttpServletResponse response, Model model) {
        noStore(response);
        model.addAttribute("staffSection", "stays");
        Filters filters = filters(referenceFilter, arrivalFilter, stateFilter, page);
        Booking booking = bookings.findByReference(reference);
        Payment payment = paymentOrNull(reference);
        StaffBookingView view = view(booking, payment);
        model.addAttribute("entry", view);
        model.addAttribute("referenceFilter", filters.reference());
        model.addAttribute("arrivalFilter", filters.arrival() == null ? "" : filters.arrival().toString());
        model.addAttribute("stateFilter", filters.state() == null ? "ALL" : filters.state().name());
        model.addAttribute("page", filters.page());
        model.addAttribute("canReconcile", payment != null
                && payment.purpose() == PaymentPurpose.BOOKING
                && payment.status() == PaymentStatus.UNDER_REVIEW);
        return "staffstay/admin-detail";
    }

    @PostMapping("/{reference}/payment/confirm")
    String confirmPayment(@PathVariable String reference,
            @RequestParam(defaultValue = "") String referenceFilter,
            @RequestParam(defaultValue = "") String arrivalFilter,
            @RequestParam(required = false) String stateFilter,
            @RequestParam(defaultValue = "0") int page,
            HttpServletResponse response, RedirectAttributes redirect) {
        noStore(response);
        try {
            Payment payment = requiredBookingPayment(reference);
            payments.staffConfirmPaid(payment.providerReference());
            redirect.addFlashAttribute("staffMessage", "支付结果已确认，预订状态已更新为已支付。");
        } catch (PaymentException | StaffStayUiException exception) {
            redirect.addFlashAttribute("staffError", readable(exception));
        }
        return redirectDetail(reference, referenceFilter, arrivalFilter, stateFilter, page);
    }

    @PostMapping("/{reference}/payment/reject")
    String rejectPayment(@PathVariable String reference,
            @RequestParam(defaultValue = "") String reason,
            @RequestParam(defaultValue = "") String referenceFilter,
            @RequestParam(defaultValue = "") String arrivalFilter,
            @RequestParam(required = false) String stateFilter,
            @RequestParam(defaultValue = "0") int page,
            HttpServletResponse response, RedirectAttributes redirect) {
        noStore(response);
        String normalizedReason = reason == null ? "" : reason.strip();
        if (normalizedReason.length() < 3 || normalizedReason.length() > 255) {
            redirect.addFlashAttribute("staffError", "拒绝原因须为 3–255 个字符，并记录真实核对依据。");
            return redirectDetail(reference, referenceFilter, arrivalFilter, stateFilter, page);
        }
        try {
            Payment payment = requiredBookingPayment(reference);
            payments.staffReject(payment.providerReference(), normalizedReason);
            redirect.addFlashAttribute("staffMessage", "支付结果已拒绝，预订已标记为支付失败。");
        } catch (PaymentException | StaffStayUiException exception) {
            redirect.addFlashAttribute("staffError", readable(exception));
        }
        return redirectDetail(reference, referenceFilter, arrivalFilter, stateFilter, page);
    }

    @ExceptionHandler(StaffStayUiException.class)
    String stayError(StaffStayUiException exception, HttpServletResponse response, Model model,
            Authentication authentication) {
        response.setStatus(exception.status());
        noStore(response);
        model.addAttribute("staffSection", "stays");
        model.addAttribute("staffStayError", exception.getMessage());
        navigation.addToErrorModel(model, authentication);
        return "staffstay/admin-error";
    }

    @ExceptionHandler(BookingException.class)
    String bookingError(BookingException exception, HttpServletResponse response, Model model,
            Authentication authentication) {
        response.setStatus(switch (exception.kind()) {
            case NOT_FOUND -> 404;
            case INVALID -> 400;
            case CONFLICT -> 409;
        });
        noStore(response);
        model.addAttribute("staffSection", "stays");
        model.addAttribute("staffStayError", readable(exception));
        navigation.addToErrorModel(model, authentication);
        return "staffstay/admin-error";
    }

    @ExceptionHandler(PaymentException.class)
    String paymentError(PaymentException exception, HttpServletResponse response, Model model,
            Authentication authentication) {
        response.setStatus(switch (exception.kind()) {
            case NOT_FOUND -> 404;
            case INVALID -> 400;
            case CONFLICT, PROVIDER_ERROR -> 409;
        });
        noStore(response);
        model.addAttribute("staffSection", "stays");
        model.addAttribute("staffStayError", readable(exception));
        navigation.addToErrorModel(model, authentication);
        return "staffstay/admin-error";
    }

    private Payment requiredBookingPayment(String reference) {
        Booking booking = bookings.findByReference(reference);
        Payment payment = paymentOrNull(reference);
        if (payment == null) {
            throw new StaffStayUiException(404, "该预订没有可核对的支付记录。");
        }
        if (payment.purpose() != PaymentPurpose.BOOKING
                || !reference.equals(payment.bookingReference())
                || !booking.reference().equals(reference)) {
            throw new StaffStayUiException(409, "支付记录与当前预订不匹配，已阻止操作。");
        }
        return payment;
    }

    private StaffBookingView view(Booking booking, Payment payment) {
        RoomType roomType = rooms.listRoomTypes().stream()
                .filter(type -> type.id().equals(booking.roomTypeId()))
                .findFirst().orElse(null);
        return new StaffBookingView(booking, payment,
                roomType == null ? booking.roomTypeId() : roomType.name(),
                money(booking.price().totalMinor()),
                money(booking.price().subtotalMinor()),
                money(booking.price().subtotalMinor() - booking.price().totalMinor()),
                String.join(", ", booking.allocatedRoomIds()),
                bookingStatusLabel(booking.status()),
                paymentStatusLabel(payment),
                payment == null ? "" : payment.failureReason(),
                payment == null ? "" : payment.providerReference());
    }

    private Payment paymentOrNull(String reference) {
        try {
            return payments.findByBookingReference(reference);
        } catch (PaymentException exception) {
            return null;
        }
    }

    private static void addNavigation(Model model, Filters filters, int page, int pageCount, int total) {
        model.addAttribute("referenceFilter", filters.reference());
        model.addAttribute("arrivalFilter", filters.arrival() == null ? "" : filters.arrival().toString());
        model.addAttribute("stateFilter", filters.state() == null ? "ALL" : filters.state().name());
        model.addAttribute("page", page);
        model.addAttribute("pageCount", pageCount);
        model.addAttribute("totalEntries", total);
        model.addAttribute("hasPrevious", page > 0);
        model.addAttribute("hasNext", page + 1 < pageCount);
    }

    private static Filters filters(String reference, String arrival, String state, int page) {
        String normalizedReference = reference == null ? "" : reference.strip().toUpperCase(Locale.ROOT);
        if (!normalizedReference.matches("[A-Z0-9-]{0,40}")) {
            throw new StaffStayUiException(400, "预订编号须为最多 40 位字母、数字或短横线。");
        }
        LocalDate parsedArrival = null;
        if (arrival != null && !arrival.isBlank()) {
            try {
                if (!arrival.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    throw new DateTimeParseException("ISO date required", arrival, 0);
                }
                parsedArrival = LocalDate.parse(arrival);
            } catch (DateTimeParseException exception) {
                throw new StaffStayUiException(400, "入住日期须为有效的年-月-日，例如 2026-11-08。");
            }
        }
        BookingStatus parsedState = null;
        String normalizedState = state == null || state.isBlank() ? "ALL" : state.strip().toUpperCase(Locale.ROOT);
        if (!normalizedState.equals("ALL")) {
            try {
                parsedState = BookingStatus.valueOf(normalizedState);
            } catch (IllegalArgumentException exception) {
                throw new StaffStayUiException(400, "请选择列表中的预订状态。");
            }
        }
        if (page < 0) {
            throw new StaffStayUiException(400, "页码不能小于 0。");
        }
        return new Filters(normalizedReference, parsedArrival, parsedState, page);
    }

    private static Filters safeFilters(String reference, String arrival, String state, int page) {
        String normalizedReference = reference == null ? "" : reference.strip().toUpperCase(Locale.ROOT);
        if (!normalizedReference.matches("[A-Z0-9-]{0,40}")) {
            normalizedReference = "";
        }
        LocalDate parsedArrival = null;
        try {
            if (arrival != null && arrival.matches("\\d{4}-\\d{2}-\\d{2}")) {
                parsedArrival = LocalDate.parse(arrival);
            }
        } catch (DateTimeParseException ignored) {
            // Invalid filter input is already represented by filterError.
        }
        BookingStatus parsedState = null;
        try {
            if (state != null && !state.isBlank() && !state.equalsIgnoreCase("ALL")) {
                parsedState = BookingStatus.valueOf(state.toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid filter input is already represented by filterError.
        }
        return new Filters(normalizedReference, parsedArrival, parsedState, Math.max(0, page));
    }

    private static List<StatusOption> stateOptions() {
        List<StatusOption> options = new java.util.ArrayList<>();
        options.add(new StatusOption("ALL", "全部状态"));
        for (BookingStatus status : BookingStatus.values()) {
            options.add(new StatusOption(status.name(), bookingStatusLabel(status)));
        }
        return List.copyOf(options);
    }

    private static String redirectDetail(String reference, String referenceFilter, String arrivalFilter,
            String stateFilter, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/stays/{reference}");
        if (referenceFilter != null && !referenceFilter.isBlank()) {
            builder.queryParam("referenceFilter", referenceFilter);
        }
        if (arrivalFilter != null && !arrivalFilter.isBlank()) {
            builder.queryParam("arrivalFilter", arrivalFilter);
        }
        if (stateFilter != null && !stateFilter.isBlank()) {
            builder.queryParam("stateFilter", stateFilter);
        }
        if (page > 0) {
            builder.queryParam("page", page);
        }
        return "redirect:" + builder.buildAndExpand(reference).encode().toUriString();
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

    private static String readable(RuntimeException exception) {
        return exception.getMessage() == null ? "请求无法完成。" : exception.getMessage();
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

    private record Filters(String reference, LocalDate arrival, BookingStatus state, int page) {
    }

    public record StatusOption(String value, String label) {
    }

    public record StaffBookingView(Booking booking, Payment payment, String roomTypeName,
            String totalLabel, String subtotalLabel, String discountAmountLabel,
            String allocatedRoomsLabel, String bookingStatusLabel, String paymentStatusLabel,
            String failureReason, String providerReference) {
    }
}