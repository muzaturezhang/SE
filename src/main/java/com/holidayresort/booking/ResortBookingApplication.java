package com.holidayresort.booking;

import com.holidayresort.booking.config.ResortProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(ResortProperties.class)
public class ResortBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResortBookingApplication.class, args);
    }
}
