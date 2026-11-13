package com.holidayresort.booking.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/** A-owned Session login for CUSTOMER and STAFF plus shared authorization rules. */
@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        http.authorizeHttpRequests(authorize -> {
            if (environment.matchesProfiles("account-ui")) {
                authorize.requestMatchers("/preview/account", "/preview/account/**").permitAll();
            }
            if (environment.matchesProfiles("booking-ui")) {
                authorize.requestMatchers("/preview/stays", "/preview/stays/**").permitAll();
            }
            authorize
                .requestMatchers("/", "/rooms", "/account/login", "/account/register",
                        "/css/**", "/js/**", "/images/**", "/vendor/**", "/error",
                        "/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/account/**").hasRole("CUSTOMER")
                .requestMatchers("/admin/**").hasRole("STAFF")
                .anyRequest().denyAll();
        });
        http.formLogin(form -> form
                .loginPage("/account/login")
                .loginProcessingUrl("/account/login")
                .successHandler(new RoleAwareAuthenticationSuccessHandler())
                .failureUrl("/account/login?error"));
        http.logout(logout -> logout.logoutSuccessUrl("/rooms"));
        // Account pages route anonymous users to the login form; every other
        // protected or unknown path stays a plain 403, matching earlier tests.
        http.exceptionHandling(handling -> handling
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/account/login"),
                        PathPatternRequestMatcher.withDefaults().matcher("/account/**"))
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.FORBIDDEN),
                        PathPatternRequestMatcher.withDefaults().matcher("/**")));
        // CSRF remains enabled.
        return http.build();
    }

    private static final class RoleAwareAuthenticationSuccessHandler implements AuthenticationSuccessHandler {
        private final SavedRequestAwareAuthenticationSuccessHandler customerHandler =
                new SavedRequestAwareAuthenticationSuccessHandler();

        RoleAwareAuthenticationSuccessHandler() {
            customerHandler.setDefaultTargetUrl("/account");
        }

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                Authentication authentication) throws IOException, ServletException {
            boolean staff = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_STAFF".equals(authority.getAuthority()));
            if (staff) {
                response.sendRedirect(request.getContextPath() + "/admin/rooms");
                return;
            }
            customerHandler.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
