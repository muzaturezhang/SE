package com.holidayresort.booking.web.payment;

import com.holidayresort.booking.payment.application.PaymentService;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentPurpose;
import com.holidayresort.booking.payment.infrastructure.SandboxPaymentProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Development/test payment simulation; never enabled in a live-payment profile. */
@Controller
@Profile("!live-payment")
@RequestMapping("/sandbox/payments")
public class SandboxPaymentController {

    private final PaymentService payments;
    private final SandboxPaymentProvider provider;

    public SandboxPaymentController(PaymentService payments, SandboxPaymentProvider provider) {
        this.payments = payments;
        this.provider = provider;
    }

    @GetMapping("/{providerReference}")
    String page(@PathVariable String providerReference, Authentication authentication,
            HttpServletResponse response, Model model) {
        Payment payment = ownedPayment(providerReference, authentication);
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("payment", payment);
        model.addAttribute("amountLabel", formatMinor(payment.amountMinor()));
        return "sandbox/payment";
    }

    @PostMapping("/{providerReference}/success")
    String success(@PathVariable String providerReference, Authentication authentication,
            RedirectAttributes redirect) {
        ownedPayment(providerReference, authentication);
        provider.simulateSuccess(providerReference);
        payments.handleCallback(providerReference);
        redirect.addFlashAttribute("accountMessage", "支付成功，会员状态已更新。");
        return redirectAfter(providerReference);
    }

    @PostMapping("/{providerReference}/failed")
    String failed(@PathVariable String providerReference, Authentication authentication,
            RedirectAttributes redirect) {
        ownedPayment(providerReference, authentication);
        provider.simulateFailure(providerReference, "Sandbox payment declined");
        payments.handleCallback(providerReference);
        redirect.addFlashAttribute("memberError", "支付失败，会员未激活。");
        return redirectAfter(providerReference);
    }

    @PostMapping("/{providerReference}/unknown")
    String unknown(@PathVariable String providerReference, Authentication authentication,
            RedirectAttributes redirect) {
        ownedPayment(providerReference, authentication);
        provider.simulateUnknown(providerReference);
        payments.handleCallback(providerReference);
        redirect.addFlashAttribute("memberError", "支付结果未确认，请等待工作人员核对。");
        return redirectAfter(providerReference);
    }

    private Payment ownedPayment(String providerReference, Authentication authentication) {
        Payment payment = payments.findByProviderReference(providerReference);
        if (authentication == null || !authentication.isAuthenticated()
                || !authentication.getName().equals(payment.customerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return payment;
    }

    private static String redirectAfter(String providerReference) {
        return "redirect:/account/membership";
    }

    private static String formatMinor(long minor) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "CNY " + format.format(BigDecimal.valueOf(minor, 2));
    }
}
