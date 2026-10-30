package com.holidayresort.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** A owns the future Session login and CUSTOMER/STAFF authorization integration. */
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
                .requestMatchers("/admin/**").hasRole("STAFF")
                .requestMatchers("/", "/rooms", "/css/**", "/js/**", "/images/**", "/vendor/**", "/error",
                        "/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().denyAll();
        });
        if (environment.matchesProfiles("local-staff")) {
            http.formLogin(form -> form.loginPage("/staff-preview/login")
                    .defaultSuccessUrl("/admin/rooms", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/rooms"));
        }
        // CSRF remains enabled. The default profile supplies no users or login provider.
        return http.build();
    }
}
