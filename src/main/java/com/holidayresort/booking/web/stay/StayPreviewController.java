package com.holidayresort.booking.web.stay;

import com.holidayresort.booking.room.application.InvalidRoomSearchException;
import com.holidayresort.booking.room.application.RoomQueryService;
import com.holidayresort.booking.web.StaffNavigationAdvice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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

/** Preview routes deliberately kept separate from future authenticated customer routes. */
@Controller
@Profile("booking-ui")
@RequestMapping("/preview/stays")
public class StayPreviewController {
    private final StayPageGateway gateway;
    private final RoomQueryService rooms;
    private final Clock clock;
    private final StaffNavigationAdvice navigation;

    public StayPreviewController(StayPageGateway gateway, RoomQueryService rooms, Clock clock, StaffNavigationAdvice navigation) {
        this.gateway = gateway;
        this.rooms = rooms;
        this.clock = clock;
        this.navigation = navigation;
    }

    @InitBinder("stayForm")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("roomType", "arrival", "departure", "rooms", "guestName", "guestEmail");
    }

    @ModelAttribute
    void previewModel(HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("previewBase", "/preview/stays");
        model.addAttribute("bookingUiPreview", true);
        model.addAttribute("today", LocalDate.now(clock));
        model.addAttribute("roomTypes", rooms.listRoomTypes());
    }

    @ModelAttribute("stayForm")
    StayForm defaultForm(HttpServletRequest request) {
        StayForm form = new StayForm();
        if (request.getMethod().equals("GET")) {
            form.setRoomType(rooms.listRoomTypes().get(0).id());
            form.setArrival(LocalDate.now(clock).plusDays(10));
            form.setDeparture(LocalDate.now(clock).plusDays(12));
            form.setRooms(1);
            form.setGuestName("演示住客");
            form.setGuestEmail("guest@example.invalid");
        }
        return form;
    }

    @GetMapping("/new")
    String confirmation(@Valid @ModelAttribute("stayForm") StayForm form, BindingResult errors,
            HttpServletResponse response, Model model) {
        loadQuote(form, errors, response, model);
        return "stay/confirm";
    }

    @PostMapping
    String submit(@Valid @ModelAttribute("stayForm") StayForm form, BindingResult errors,
            HttpServletRequest request, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        loadQuote(form, errors, response, model);
        if (!errors.hasErrors()) {
            try {
                var reservation = gateway.submit(viewer(request), new StayPageGateway.Submission(
                        form.selection(), form.getGuestName(), form.getGuestEmail()));
                redirect.addFlashAttribute("previewMessage", "仅生成本会话的演示草稿，未创建实际订单、占用库存或发起支付。");
                return "redirect:/preview/stays/" + reservation.reference();
            } catch (InvalidRoomSearchException | StayUiException exception) {
                reject(exception, errors, response);
                messages(errors, model);
            }
        }
        return "stay/confirm";
    }

    @GetMapping
    String list(@RequestParam(defaultValue = "all") String example, HttpServletRequest request, Model model) {
        if (!example.equals("all") && !example.equals("empty")) throw new StayUiException(400, "请选择有效的列表演示状态。");
        model.addAttribute("reservations", example.equals("empty") ? java.util.List.of() : gateway.listFor(viewer(request)));
        model.addAttribute("emptyExample", example.equals("empty"));
        return "stay/index";
    }

    @GetMapping("/{reference}")
    String detail(@PathVariable String reference, HttpServletRequest request, Model model) {
        model.addAttribute("reservation", requireReservation(request, reference));
        return "stay/detail";
    }

    @GetMapping("/{reference}/payment")
    String payment(@PathVariable String reference, HttpServletRequest request, Model model) {
        model.addAttribute("reservation", requireReservation(request, reference));
        return "stay/payment";
    }

    private StayPageGateway.Reservation requireReservation(HttpServletRequest request, String reference) {
        return gateway.findFor(viewer(request), reference).orElseThrow(() -> new StayUiException(404,
                "找不到这条演示记录。草稿只属于当前会话，重启或更换会话后需重新填写。"));
    }

    private void loadQuote(StayForm form, BindingResult errors, HttpServletResponse response, Model model) {
        if (!errors.hasErrors()) {
            try {
                model.addAttribute("quote", gateway.quote(form.selection()));
            } catch (InvalidRoomSearchException | StayUiException exception) {
                reject(exception, errors, response);
            }
        }
        if (errors.hasErrors() && response.getStatus() == 200) response.setStatus(400);
        messages(errors, model);
    }

    private void reject(RuntimeException exception, BindingResult errors, HttpServletResponse response) {
        if (exception instanceof InvalidRoomSearchException invalid) {
            errors.rejectValue(invalid.field(), "stay.invalid", invalid.getMessage());
            response.setStatus(400);
        } else {
            errors.reject("stay.unavailable", exception.getMessage());
            response.setStatus(((StayUiException) exception).status());
        }
    }

    private void messages(BindingResult errors, Model model) {
        model.addAttribute("formErrors", errors.getAllErrors().stream().map(error ->
                error instanceof org.springframework.validation.FieldError field && field.isBindingFailure()
                        ? "日期须为有效的年-月-日，房间数量须为正整数。" : error.getDefaultMessage()).distinct().toList());
    }

    private String viewer(HttpServletRequest request) { return request.getSession().getId(); }

    @ExceptionHandler(StayUiException.class)
    String error(StayUiException exception, HttpServletResponse response, Model model, Authentication authentication) {
        response.setStatus(exception.status());
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("previewBase", "/preview/stays");
        model.addAttribute("bookingUiPreview", true);
        model.addAttribute("stayError", exception.getMessage());
        navigation.addToErrorModel(model, authentication);
        return "stay/error";
    }
}
