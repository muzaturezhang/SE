package com.holidayresort.booking.web.account;

import com.holidayresort.booking.web.StaffNavigationAdvice;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/** UI-only preview, deliberately separate from A's future authentication controllers. */
@Controller
@Profile("account-ui")
@RequestMapping("/preview/account")
public class AccountPreviewController {
    private final AccountPageGateway gateway;
    private final StaffNavigationAdvice navigation;

    public AccountPreviewController(AccountPageGateway gateway, StaffNavigationAdvice navigation) {
        this.gateway = gateway;
        this.navigation = navigation;
    }

    @InitBinder("loginForm")
    void loginFields(WebDataBinder binder) { binder.setAllowedFields("email", "password", "next"); }

    @InitBinder("registerForm")
    void registerFields(WebDataBinder binder) {
        binder.setAllowedFields("displayName", "email", "password", "confirmPassword", "next");
    }

    @ModelAttribute
    void previewModel(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("accountPreviewActive", gateway.profileFor(viewer(request)).isPresent());
    }

    @GetMapping("/login")
    String login(@RequestParam(defaultValue = "") String next,
            @RequestParam(defaultValue = "default") String example, Model model, HttpServletResponse response,
            HttpServletRequest request) {
        if (!List.of("default", "denied").contains(example)) throw new AccountUiException(400, "请选择有效的登录展示状态。");
        AccountLoginForm form = new AccountLoginForm();
        form.setEmail(gateway.registrationFor(viewer(request)).map(AccountPageGateway.Profile::email)
                .orElse("guest@example.invalid"));
        String target = getTarget(next, model, response);
        form.setNext(target);
        model.addAttribute("loginForm", form);
        if (example.equals("denied") && response.getStatus() == 200) model.addAttribute("formErrors", List.of("固定示例：邮箱或密码不正确，请重新输入。"));
        return "account/login";
    }

    @PostMapping("/login")
    String previewLogin(@Valid @ModelAttribute("loginForm") AccountLoginForm form, BindingResult errors,
            HttpServletRequest request, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        String target = postTarget(form, errors, response, model);
        form.clearSecrets();
        if (errors.hasErrors()) return invalidForm("account/login", errors, response, model);
        gateway.previewSignIn(viewer(request), form.getEmail());
        redirect.addFlashAttribute("accountMessage", "已切换到登录后页面演示；没有进行真实认证或改变系统权限。");
        return "redirect:" + target;
    }

    @GetMapping("/register")
    String register(@RequestParam(defaultValue = "") String next,
            @RequestParam(defaultValue = "default") String example, Model model, HttpServletResponse response) {
        if (!List.of("default", "duplicate").contains(example)) throw new AccountUiException(400, "请选择有效的注册展示状态。");
        AccountRegistrationForm form = new AccountRegistrationForm();
        form.setDisplayName("演示住客");
        form.setEmail("guest@example.invalid");
        form.setNext(getTarget(next, model, response));
        model.addAttribute("registerForm", form);
        if (example.equals("duplicate") && response.getStatus() == 200) model.addAttribute("formErrors", List.of("固定示例：该邮箱已注册，请登录或使用其他邮箱。"));
        return "account/register";
    }

    @PostMapping("/register")
    String previewRegistration(@Valid @ModelAttribute("registerForm") AccountRegistrationForm form, BindingResult errors,
            HttpServletRequest request, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        String target = postTarget(form, errors, response, model);
        form.clearSecrets();
        if (errors.hasErrors()) return invalidForm("account/register", errors, response, model);
        gateway.previewRegistration(viewer(request), form.getDisplayName(), form.getEmail());
        return "redirect:" + UriComponentsBuilder.fromPath("/preview/account/registration-result")
                .queryParam("next", target).build().encode().toUriString();
    }

    @GetMapping("/registration-result")
    String registrationResult(@RequestParam(defaultValue = "") String next, HttpServletRequest request, Model model) {
        String target = AccountReturnTarget.checked(next);
        model.addAttribute("returnTarget", target);
        model.addAttribute("returnLabel", AccountReturnTarget.label(target));
        model.addAttribute("registration", gateway.registrationFor(viewer(request))
                .orElseThrow(() -> new AccountUiException(404, "当前会话还没有注册表单演示结果，请先填写注册页面。")));
        return "account/registration-result";
    }

    @GetMapping
    String profile(HttpServletRequest request, Model model, RedirectAttributes redirect) {
        var profile = gateway.profileFor(viewer(request));
        if (profile.isEmpty()) return loginRedirect(AccountReturnTarget.HOME);
        model.addAttribute("profile", profile.get());
        model.addAttribute("membership", gateway.membershipExample("none"));
        return "account/profile";
    }

    @PostMapping("/logout")
    String clear(HttpServletRequest request, RedirectAttributes redirect) {
        gateway.clearPreview(viewer(request));
        redirect.addFlashAttribute("accountMessage", "账户页面演示已退出，正式登录身份和其他页面草稿未改变。");
        return "redirect:/preview/account/login";
    }

    @GetMapping("/membership")
    String membership(@RequestParam(defaultValue = "none") String example, Model model) {
        model.addAttribute("membership", gateway.membershipExample(example));
        model.addAttribute("selectedMemberExample", example);
        return "account/membership";
    }

    @GetMapping("/membership/join")
    String join(@RequestParam(defaultValue = "join") String intent, HttpServletRequest request,
            Model model, RedirectAttributes redirect) {
        if (!List.of("join", "renew").contains(intent)) throw new AccountUiException(400, "请选择有效的开通或续费展示入口。");
        if (gateway.profileFor(viewer(request)).isEmpty()) {
            return loginRedirect("/preview/account/membership/join?intent=" + intent);
        }
        model.addAttribute("intent", intent);
        model.addAttribute("membership", gateway.membershipExample(intent.equals("renew") ? "expired" : "none"));
        return "account/join";
    }

    private String getTarget(String next, Model model, HttpServletResponse response) {
        String target;
        try { target = AccountReturnTarget.checked(next); }
        catch (AccountUiException exception) {
            target = AccountReturnTarget.HOME;
            response.setStatus(400);
            model.addAttribute("formErrors", List.of(exception.getMessage()));
        }
        model.addAttribute("returnTarget", target);
        model.addAttribute("returnLabel", AccountReturnTarget.label(target));
        return target;
    }

    private String postTarget(AccountLoginForm form, BindingResult errors, HttpServletResponse response, Model model) {
        String target;
        try { target = AccountReturnTarget.checked(form.getNext()); }
        catch (AccountUiException exception) {
            target = AccountReturnTarget.HOME;
            errors.reject("account.return.invalid", exception.getMessage());
            response.setStatus(400);
        }
        form.setNext(target);
        model.addAttribute("returnTarget", target);
        model.addAttribute("returnLabel", AccountReturnTarget.label(target));
        return target;
    }

    private String invalidForm(String view, BindingResult errors, HttpServletResponse response, Model model) {
        response.setStatus(400);
        model.addAttribute("formErrors", errors.getAllErrors().stream().map(error -> error.getDefaultMessage()).distinct().toList());
        return view;
    }

    private String loginRedirect(String target) {
        return "redirect:" + UriComponentsBuilder.fromPath("/preview/account/login")
                .queryParam("next", target).build().encode().toUriString();
    }

    private String viewer(HttpServletRequest request) { return request.getSession().getId(); }

    @ExceptionHandler(AccountUiException.class)
    String error(AccountUiException exception, HttpServletRequest request, HttpServletResponse response,
            Model model, Authentication authentication) {
        response.setStatus(exception.status());
        response.setHeader("Cache-Control", "no-store");
        navigation.addToErrorModel(model, authentication);
        model.addAttribute("accountPreviewActive", gateway.profileFor(viewer(request)).isPresent());
        model.addAttribute("accountError", exception.getMessage());
        return "account/error";
    }
}
