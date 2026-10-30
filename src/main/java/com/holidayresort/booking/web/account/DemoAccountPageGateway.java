package com.holidayresort.booking.web.account;

import static com.holidayresort.booking.web.account.AccountPageGateway.MemberDisplayState.*;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/** A Session presentation scratchpad; receives no passwords and changes no security identity. */
@Component
@SessionScope
@Profile("account-ui")
public class DemoAccountPageGateway implements AccountPageGateway {
    private final HttpSession session;
    private final LocalDate fixtureDate;
    private Profile presentation;
    private Profile registration;

    public DemoAccountPageGateway(HttpSession session, Clock clock) {
        this.session = session;
        this.fixtureDate = LocalDate.now(clock);
    }

    @Override
    public synchronized Optional<Profile> profileFor(String viewerId) {
        return session.getId().equals(viewerId) ? Optional.ofNullable(presentation) : Optional.empty();
    }

    @Override
    public synchronized void previewSignIn(String viewerId, String email) {
        requireViewer(viewerId);
        String contactEmail = email.strip();
        String displayName = registration != null && registration.email().equals(contactEmail)
                ? registration.displayName() : "演示住客";
        presentation = new Profile(displayName, contactEmail);
    }

    @Override
    public synchronized void previewRegistration(String viewerId, String displayName, String email) {
        requireViewer(viewerId);
        registration = new Profile(displayName.strip(), email.strip());
    }

    @Override
    public synchronized Optional<Profile> registrationFor(String viewerId) {
        return session.getId().equals(viewerId) ? Optional.ofNullable(registration) : Optional.empty();
    }

    @Override
    public synchronized void clearPreview(String viewerId) {
        requireViewer(viewerId);
        presentation = null;
        registration = null;
    }

    @Override
    public Membership membershipExample(String example) {
        String discount = "待会员服务返回";
        String validity = "实际有效期由会员服务确认";
        // Relative dates only populate static UI examples, never determine real membership eligibility.
        return switch (example) {
            case "none" -> new Membership(NONE, null, null, null, discount, validity);
            case "active" -> new Membership(ACTIVE, fixtureDate.minusDays(20), fixtureDate.plusDays(40), null, discount, validity);
            case "expired" -> new Membership(EXPIRED, fixtureDate.minusDays(60), fixtureDate.minusDays(7), null, discount, validity);
            case "pending" -> new Membership(PENDING, null, null, null, discount, validity);
            default -> throw new AccountUiException(400, "请选择有效的会员展示状态。");
        };
    }

    private void requireViewer(String viewerId) {
        if (!session.getId().equals(viewerId)) throw new AccessDeniedException("Preview session does not match");
    }
}
