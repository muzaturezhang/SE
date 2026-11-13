package com.holidayresort.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Keeps B's loopback-only staff preview working: under the local-staff profile
 * this chain owns /staff-preview/login and /admin/** ahead of the main chain.
 */
@Configuration
@Profile("local-staff")
public class StaffPreviewSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain staffPreviewFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/staff-preview/login", "/admin/**");
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/staff-preview/login").permitAll()
                .requestMatchers("/admin/**").hasRole("STAFF")
                .anyRequest().denyAll());
        http.formLogin(form -> form
                .loginPage("/staff-preview/login")
                .loginProcessingUrl("/staff-preview/login")
                .defaultSuccessUrl("/admin/rooms", true)
                .failureUrl("/staff-preview/login?error"));
        return http.build();
    }
}
