package com.holidayresort.booking.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".+")
class MySqlLiveIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void mysqlEightAppliesMigrationsAndSeedCatalogue() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isGreaterThanOrEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_type", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room", Integer.class))
                .isEqualTo(8);
    }
}
