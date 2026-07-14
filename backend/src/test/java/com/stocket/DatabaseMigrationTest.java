package com.stocket;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
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

    @Test
    void createsIdentityAndAuditSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('household', 'user_account', 'household_member',
                                     'member_invite', 'user_session', 'audit_log')
                order by table_name
                """, String.class))
                .containsExactly("audit_log", "household", "household_member",
                        "member_invite", "user_account", "user_session");

        jdbc.update("insert into household(id, singleton_key, name, timezone) values (?, 1, ?, ?)",
                UUID.randomUUID(), "家", "Asia/Shanghai");
        assertThatThrownBy(() -> jdbc.update(
                "insert into household(id, singleton_key, name, timezone) values (?, 1, ?, ?)",
                UUID.randomUUID(), "第二个家", "Asia/Shanghai"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void appliesAllMigrationsAndCreatesCatalogLocationSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForList("""
                select version
                from flyway_schema_history
                where success = true
                order by installed_rank
                """, String.class))
                .containsExactly("1", "2", "3", "4", "5", "6", "7");

        assertThat(jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('category', 'location', 'item_definition',
                                     'item_barcode', 'item_tag', 'catalog_search_projection')
                order by table_name
                """, String.class))
                .containsExactly("catalog_search_projection", "category", "item_barcode",
                        "item_definition", "item_tag", "location");
    }
}
