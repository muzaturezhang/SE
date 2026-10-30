package com.holidayresort.booking.web.staffstay;

import com.holidayresort.booking.web.StaffNavigationAdvice;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Profile("staff-booking-ui")
@RequestMapping("/admin/stays")
public class StaffStayPreviewController {
    private final StaffStayPageGateway gateway;
    private final StaffNavigationAdvice navigation;
    public StaffStayPreviewController(StaffStayPageGateway gateway, StaffNavigationAdvice navigation) {
        this.gateway = gateway; this.navigation = navigation;
    }
    @ModelAttribute void model(HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("staffSection", "stays");
        model.addAttribute("states", StaffStayPageGateway.State.values());
    }
    @GetMapping String list(@RequestParam(defaultValue = "") String reference,
            @RequestParam(defaultValue = "") String arrival, @RequestParam(required = false) String state,
            Authentication authentication, HttpServletResponse response, Model model) {
        state = state == null ? "all" : state;
        filters(model, reference, arrival, state);
        try {
            model.addAttribute("entries", gateway.search(authentication.getName(), query(reference, arrival, state)));
        } catch (StaffStayUiException exception) {
            response.setStatus(exception.status());
            model.addAttribute("filterError", exception.getMessage());
            model.addAttribute("entries", List.of());
        }
        return "staffstay/index";
    }
    @GetMapping("/{id}") String detail(@PathVariable String id, @RequestParam(defaultValue = "") String reference,
            @RequestParam(defaultValue = "") String arrival, @RequestParam(required = false) String state,
            Authentication authentication, Model model) {
        state = state == null ? "all" : state;
        query(reference, arrival, state); // Only known filter fields; never accept an arbitrary return URL.
        filters(model, reference, arrival, state);
        model.addAttribute("entry", gateway.find(authentication.getName(), id)
                .orElseThrow(() -> new StaffStayUiException(404, "找不到这条演示预订，请返回列表重新选择。")));
        return "staffstay/detail";
    }
    private void filters(Model model, String reference, String arrival, String state) {
        model.addAttribute("referenceFilter", reference);
        model.addAttribute("arrivalFilter", arrival);
        model.addAttribute("stateFilter", state);
    }
    private StaffStayPageGateway.Query query(String reference, String arrival, String state) {
        LocalDate date = null;
        if (!arrival.isEmpty()) {
            try {
                if (!arrival.matches("\\d{4}-\\d{2}-\\d{2}")) throw new DateTimeParseException("ISO date required", arrival, 0);
                date = LocalDate.parse(arrival);
            } catch (DateTimeParseException exception) {
                throw new StaffStayUiException(400, "入住日期须为有效的年-月-日，例如 2026-11-08。");
            }
        }
        StaffStayPageGateway.State selected = null;
        if (!state.equals("all")) {
            try { selected = StaffStayPageGateway.State.valueOf(state.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { throw new StaffStayUiException(400, "请选择列表中的预订状态。"); }
        }
        return new StaffStayPageGateway.Query(reference, date, selected);
    }
    @ExceptionHandler(StaffStayUiException.class)
    String error(StaffStayUiException exception, HttpServletResponse response, Model model, Authentication authentication) {
        response.setStatus(exception.status());
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("staffSection", "stays");
        model.addAttribute("staffStayError", exception.getMessage());
        navigation.addToErrorModel(model, authentication);
        return "staffstay/error";
    }
}
