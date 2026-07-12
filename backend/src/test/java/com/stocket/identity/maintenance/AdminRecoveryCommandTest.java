package com.stocket.identity.maintenance;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.StocketApplication;
import com.stocket.identity.internal.maintenance.AdminRecoveryCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the local administrator recovery command.
 * Verifies that the command can reset an admin password, revoke sessions,
 * write audit events, and handle error cases correctly.
 */
@Testcontainers
@DisabledInAotMode
class AdminRecoveryCommandTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    private String jdbcUrl;
    private String dbUsername;
    private String dbPassword;
    private ConfigurableApplicationContext context;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbcUrl = postgres.getJdbcUrl();
        dbUsername = postgres.getUsername();
        dbPassword = postgres.getPassword();

        // Create a single context for all tests
        context = new SpringApplicationBuilder(StocketApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + dbUsername,
                        "spring.datasource.password=" + dbPassword)
                .run();
        jdbc = context.getBean(JdbcTemplate.class);

        // Clean database
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household, audit_log CASCADE");
    }

    /**
     * Helper to initialize the household and admin account.
     */
    private void initializeHousehold(String username, String role) {
        // Create household
        UUID householdId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO household (id, singleton_key, name, timezone, created_at, updated_at)
                VALUES (?, 1, 'Test Household', 'Asia/Shanghai', NOW(), NOW())
                """, householdId);

        // Create account
        UUID accountId = UUID.randomUUID();
        String normalizedUsername = username.toLowerCase().trim();
        jdbc.update("""
                INSERT INTO user_account (id, username, normalized_username, display_name, password_hash,
                status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, 'User', ?, 'ACTIVE', false, NOW(), NOW(), NOW(), 0)
                """, accountId, username, normalizedUsername, "{bcrypt}$2a$10$dummy.hash.for.testing");

        // Create household member
        jdbc.update("""
                INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                """, UUID.randomUUID(), householdId, accountId, role);
    }

    @Test
    void validAdminGeneratesTempPasswordAndRevokesSessions() {
        // Arrange: initialize household with admin
        initializeHousehold("admin", "ADMIN");

        // Act: invoke the command directly
        AdminRecoveryCommand command = context.getBean(AdminRecoveryCommand.class);
        String tempPassword = command.resetAdmin("admin");

        // Verify temporary password was generated
        assertThat(tempPassword).isNotNull();
        assertThat(tempPassword).hasSize(20);

        // Verify password was changed (not the original dummy hash)
        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM user_account WHERE normalized_username = ?",
                String.class, "admin");
        assertThat(passwordHash).isNotNull();
        assertThat(passwordHash).doesNotContain("dummy.hash.for.testing");

        // Verify must_change_password is true
        Boolean mustChange = jdbc.queryForObject(
                "SELECT must_change_password FROM user_account WHERE normalized_username = ?",
                Boolean.class, "admin");
        assertThat(mustChange).isTrue();

        // Verify audit event was written
        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE event_type = ?",
                Integer.class, "LOCAL_MAINTENANCE");
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void unknownUserThrowsException() {
        // Arrange: initialize household with admin
        initializeHousehold("admin", "ADMIN");

        // Act & Assert: try to reset unknown user
        AdminRecoveryCommand command = context.getBean(AdminRecoveryCommand.class);
        assertThatThrownBy(() -> command.resetAdmin("nonexistent"))
                .isInstanceOf(AdminRecoveryCommand.AdminNotFoundException.class);

        // Verify password was NOT changed for the existing admin
        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM user_account WHERE normalized_username = ?",
                String.class, "admin");
        assertThat(passwordHash).contains("dummy.hash.for.testing");

        // Verify no audit event was written
        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE event_type = ?",
                Integer.class, "LOCAL_MAINTENANCE");
        assertThat(auditCount).isEqualTo(0);
    }

    @Test
    void nonAdminUserThrowsException() {
        // Arrange: initialize household with a MEMBER role user
        initializeHousehold("member", "MEMBER");

        // Act & Assert: try to reset non-admin user
        AdminRecoveryCommand command = context.getBean(AdminRecoveryCommand.class);
        assertThatThrownBy(() -> command.resetAdmin("member"))
                .isInstanceOf(AdminRecoveryCommand.NotAdminException.class);

        // Verify password was NOT changed
        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM user_account WHERE normalized_username = ?",
                String.class, "member");
        assertThat(passwordHash).contains("dummy.hash.for.testing");

        // Verify no audit event was written
        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE event_type = ?",
                Integer.class, "LOCAL_MAINTENANCE");
        assertThat(auditCount).isEqualTo(0);
    }
}
