package com.stocket;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
class DatabaseMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private DataSource dataSource;

    @Test
    void appliesPostgresqlBaselineMigration() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from pg_extension where extname = 'pg_trgm'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from app_schema_marker where version = 1",
                Integer.class
        )).isEqualTo(1);
    }
}
