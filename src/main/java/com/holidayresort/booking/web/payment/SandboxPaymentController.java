package com.holidayresort.booking.web.payment;

import com.holidayresort.booking.payment.application.PaymentException;
import com.holidayresort.booking.payment.application.PaymentService;
import com.holidayresort.booking.payment.domain.Payment;
import com.holidayresort.booking.payment.domain.PaymentPurpose;
import com.holidayresort.booking.payment.domain.PaymentStatus;
import com.holidayresort.booking.payment.infrastructure.SandboxPaymentProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        model.addAttribute("purposeLabel", purposeLabel(payment));
        model.addAttribute("paymentStatusLabel", paymentStatusLabel(payment.status()));
        model.addAttribute("returnUrl", returnUrl(payment));
        return "sandbox/payment";
    }

    @PostMapping("/{providerReference}/success")
    String success(@PathVariable String providerReference, Authentication authentication,
            RedirectAttributes redirect) {
        Payment payment = ownedPayment(providerReference, authentication);
        provider.simulateSuccess(providerReference);
        payments.handleCallback(providerReference);
        if (payment.purpose() == PaymentPurpose.BOOKING) {
            redirect.addFlashAttribute("stayMessage", "支付成功，预订已确认。");
        } else {
            redirect.addFlashAttribute("accountMessage", "支付成功，会员状态已更新。");
        }
        return redirectAfter(payment);
    }

    @PostMapping("/{providerReference}/failed")
    String failed(@PathVariable String providerReference, Authentication authentication,
            RedirectAttributes redirect) {
        Payment payment = ownedPayment(providerReference, authentication);
        provider.simulateFailure(providerReference, "Sandbox payment declined");
        payments.handleCallback(providerReference);
        if (payment.purpose() == PaymentPurpose.BOOKING) {
            redirect.addFlashAttribute("stayError", "支付失败，预订未确认，房间已释放。");
        } else {
            redirect.addFlashAttribute("memberError", "支付失败，会员未激活。");
        }
        return redirectAfter(payment);
    }

    @PostMapping("/{providerReference}/unknown")
    String unknown(@PathVariable String providerReference, Authentication authentication,
            RedirectAttributes redirect) {
        Payment payment = ownedPayment(providerReference, authentication);
        provider.simulateUnknown(providerReference);
        payments.handleCallback(providerReference);
        if (payment.purpose() == PaymentPurpose.BOOKING) {
            redirect.addFlashAttribute("stayError", "支付结果未确认，请等待工作人员核对。");
        } else {
            redirect.addFlashAttribute("memberError", "支付结果未确认，请等待工作人员核对。");
        }
        return redirectAfter(payment);
    }

    private Payment ownedPayment(String providerReference, Authentication authentication) {
        try {
            Payment payment = payments.findByProviderReference(providerReference);
            if (authentication == null || !authentication.isAuthenticated()
                    || !authentication.getName().equals(payment.customerId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
            return payment;
        } catch (PaymentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private static String redirectAfter(Payment payment) {
        if (payment.purpose() == PaymentPurpose.BOOKING) {
            return "redirect:/stays/" + URLEncoder.encode(payment.bookingReference(), StandardCharsets.UTF_8);
        }
        return "redirect:/account/membership";
    }

    private static String returnUrl(Payment payment) {
        return payment.purpose() == PaymentPurpose.BOOKING
                ? "/stays/" + URLEncoder.encode(payment.bookingReference(), StandardCharsets.UTF_8)
                : "/account/membership";
    }

    private static String purposeLabel(Payment payment) {
        return payment.purpose() == PaymentPurpose.BOOKING ? "住宿预订" : "会员年费";
    }

    private static String paymentStatusLabel(PaymentStatus status) {
        return switch (status) {
            case NEW, PENDING -> "待支付";
            case PAID -> "支付成功";
            case FAILED -> "支付失败";
            case UNDER_REVIEW -> "待工作人员核对";
            case EXPIRED -> "支付已过期";
        };
    }

    private static String formatMinor(long minor) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.CHINA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "CNY " + format.format(BigDecimal.valueOf(minor, 2));
    }
}
