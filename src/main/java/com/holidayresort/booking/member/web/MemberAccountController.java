package com.holidayresort.booking.member.web;

import com.holidayresort.booking.member.application.MemberAccountException;
import com.holidayresort.booking.member.application.MembershipPlan;
import com.holidayresort.booking.member.application.MembershipService;
import com.holidayresort.booking.member.application.RegistrationService;
import com.holidayresort.booking.member.application.port.MemberRepository;
import com.holidayresort.booking.member.domain.Customer;
import com.holidayresort.booking.member.domain.Membership;
import com.holidayresort.booking.web.StaffNavigationAdvice;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** A-owned customer pages: real registration, profile and membership status. */
@Controller
@RequestMapping("/account")
public class MemberAccountController {

    private final MemberRepository members;
    private final RegistrationService registrationService;
    private final MembershipService membershipService;
    private final StaffNavigationAdvice staffNavigation;
    private final MemberNavigationAdvice memberNavigation;
    private final Clock clock;

    public MemberAccountController(MemberRepository members, RegistrationService registrationService,
            MembershipService membershipService, StaffNavigationAdvice staffNavigation,
            MemberNavigationAdvice memberNavigation, Clock clock) {
        this.members = members;
        this.registrationService = registrationService;
        this.membershipService = membershipService;
        this.staffNavigation = staffNavigation;
        this.memberNavigation = memberNavigation;
        this.clock = clock;
    }

    @InitBinder("registerForm")
    void registerFields(WebDataBinder binder) {
        binder.setAllowedFields("displayName", "email", "password", "confirmPassword");
    }

    @GetMapping("/login")
    String login() {
        return "member/login";
    }

    @GetMapping("/register")
    String register(Model model) {
        if (!model.containsAttribute("registerForm")) {
            model.addAttribute("registerForm", new MemberRegistrationForm());
        }
        return "member/register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("registerForm") MemberRegistrationForm form, BindingResult errors,
            HttpServletResponse response, Model model, RedirectAttributes redirect) {
        int status = 400;
        if (!errors.hasErrors()) {
            try {
                registrationService.register(form.getDisplayName(), form.getEmail(), form.getPassword());
            } catch (MemberAccountException exception) {
                status = exception.kind() == MemberAccountException.Kind.CONFLICT ? 409 : 400;
                errors.reject("member.register.failed", exception.getMessage());
            }
        }
        form.clearSecrets();
        if (errors.hasErrors()) {
            response.setStatus(status);
            response.setHeader("Cache-Control", "no-store");
            model.addAttribute("formErrors", errors.getAllErrors().stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage).distinct().toList());
            return "member/register";
        }
        redirect.addFlashAttribute("accountMessage", "注册成功，请使用邮箱和密码登录。");
        return "redirect:/account/login";
    }

    @GetMapping
    String profile(Authentication authentication, Model model) {
        Customer customer = requireCustomer(authentication);
        model.addAttribute("profile", customer);
        model.addAttribute("membershipView", membershipView(customer.id()));
        return "member/profile";
    }

    @GetMapping("/membership")
    String membership(Authentication authentication, Model model) {
        Customer customer = requireCustomer(authentication);
        model.addAttribute("membershipView", membershipView(customer.id()));
        return "member/membership";
    }

    @GetMapping("/membership/join")
    String join(@RequestParam(defaultValue = "join") String intent, Authentication authentication, Model model) {
        requireIntent(intent);
        Customer customer = requireCustomer(authentication);
        MembershipPlan plan = membershipService.planFor(customer.id());
        model.addAttribute("intent", intent);
        model.addAttribute("plan", plan);
        model.addAttribute("feeLabel", formatMinor(plan.feeMinor()));
        return "member/join";
    }

    @PostMapping("/membership/join")
    String requestMembership(@RequestParam(defaultValue = "join") String intent, Authentication authentication,
            RedirectAttributes redirect) {
        requireIntent(intent);
        Customer customer = requireCustomer(authentication);
        try {
            membershipService.requestApplication(customer.id());
        } catch (MemberAccountException exception) {
            redirect.addFlashAttribute("memberError", exception.getMessage());
            return "redirect:/account/membership";
        }
        redirect.addFlashAttribute("accountMessage", "会员申请已记录，等待支付服务确认后生效。");
        return "redirect:/account/membership";
    }

    @ExceptionHandler(MemberUiException.class)
    String error(MemberUiException exception, HttpServletResponse response, Model model,
            Authentication authentication) {
        response.setStatus(exception.status());
        response.setHeader("Cache-Control", "no-store");
        staffNavigation.addToErrorModel(model, authentication);
        memberNavigation.addToErrorModel(model, authentication);
        model.addAttribute("memberError", exception.getMessage());
        return "member/error";
    }

    private Customer requireCustomer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new MemberUiException(401, "请先登录后访问账户页面。");
        }
        return members.findById(authentication.getName())
                .orElseThrow(() -> new MemberUiException(404, "当前登录账户不存在，请重新登录。"));
    }

    private void requireIntent(String intent) {
        if (!List.of("join", "renew").contains(intent)) {
            throw new MemberUiException(400, "请选择有效的开通或续费入口。");
        }
    }

    private MembershipView membershipView(String customerId) {
        Membership membership = membershipService.statusFor(customerId).orElse(null);
        MembershipPlan plan = membershipService.planFor(customerId);
        return MembershipView.of(membership, plan, LocalDate.now(clock));
    }

    private static String formatMinor(long minor) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "CNY " + format.format(BigDecimal.valueOf(minor, 2));
    }

    /** Page projection built from domain state and the published plan. */
    public record MembershipView(String stateName, String stateLabel, String feeLabel, String discountLabel,
                                 String validityLabel, LocalDate startDate, LocalDate endDate) {

        static MembershipView of(Membership membership, MembershipPlan plan, LocalDate today) {
            if (membership == null) {
                return new MembershipView("NONE", "未开通", formatMinor(plan.feeMinor()),
                        "会员住宿 " + (100 - plan.discountPercent()) + " 折（开通后生效）",
                        "支付确认之日起 " + plan.durationDays() + " 天内有效", null, null);
            }
            Membership.State state = membership.stateOn(today);
            return switch (state) {
                case PENDING -> new MembershipView("PENDING", "待确认", formatMinor(membership.feeMinor()),
                        "会员住宿 " + (100 - membership.discountPercent()) + " 折（待确认）",
                        "会员申请已记录，等待支付服务确认", null, null);
                case ACTIVE -> new MembershipView("ACTIVE", "有效", formatMinor(membership.feeMinor()),
                        "会员住宿 " + (100 - membership.discountPercent()) + " 折",
                        "有效期内住宿享受会员折扣", membership.startDate(), membership.endDate());
                case EXPIRED -> new MembershipView("EXPIRED", "已过期", formatMinor(membership.feeMinor()),
                        "会员住宿 " + (100 - membership.discountPercent()) + " 折（需续费）",
                        "会员已过期，续费后恢复折扣", membership.startDate(), membership.endDate());
            };
        }
    }
}
