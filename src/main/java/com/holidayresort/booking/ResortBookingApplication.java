package com.holidayresort.booking;

import com.holidayresort.booking.config.ResortProperties;
import com.holidayresort.booking.config.MembershipProperties;
import com.holidayresort.booking.config.BookingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({ResortProperties.class, MembershipProperties.class, BookingProperties.class})
public class ResortBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResortBookingApplication.class, args);
    }
}
