package com.holidayresort.booking.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

    @Bean
    Clock resortClock(ResortProperties properties) {
        return Clock.system(properties.timezone());
    }
}
