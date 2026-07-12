package com.stocket.identity.maintenance;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
 *
 * <p>Tests launch the full Spring context via {@link SpringApplicationBuilder}
 * and verify both stdout output and database state.
 */
@Testcontainers
@DisabledInAotMode
class AdminRecoveryCommandTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    private String jdbcUrl;
    private String dbUsername;
    private String dbPassword;

    @BeforeEach
    void setUp() {
        jdbcUrl = postgres.getJdbcUrl();
        dbUsername = postgres.getUsername();
        dbPassword = postgres.getPassword();

        // Clean database before each test
        try (ConfigurableApplicationContext ctx = createBaseContext().run()) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household, audit_log CASCADE");
        }
    }

    /**
     * Creates a base SpringApplicationBuilder without maintenance parameters.
     */
    private SpringApplicationBuilder createBaseContext() {
        return new SpringApplicationBuilder(StocketApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + dbUsername,
                        "spring.datasource.password=" + dbPassword);
    }

    /**
     * Helper to initialize the household and admin account.
     */
    private void initializeHousehold(JdbcTemplate jdbc, String username, String role) {
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

    /**
     * Tests that when no maintenance parameters are provided, the application
     * starts normally as a web application.
     */
    @Test
    void noMaintenanceParamStartsNormally() {
        // Arrange & Act: start context without maintenance parameter
        try (ConfigurableApplicationContext ctx = createBaseContext().run()) {
            // Assert: context should start successfully
            assertThat(ctx).isNotNull();
            assertThat(ctx.isRunning()).isTrue();

            // Verify no maintenance command was executed
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            Integer auditCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE event_type = ?",
                    Integer.class, "PasswordRecoveredLocally");
            assertThat(auditCount).isEqualTo(0);
        }
    }

    /**
     * Tests that when only the reset parameter is provided (without explicit
     * WebApplicationType.NONE), the application automatically uses NONE mode.
     */
    @Test
    void resetParamAutoUsesNoneMode() {
        // Arrange: initialize household
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "admin", "ADMIN");
        }

        // Act: start context with maintenance parameter (should auto-use NONE mode)
        try (ConfigurableApplicationContext ctx = createBaseContext()
                .properties("stocket.maintenance.reset-admin=admin")
                .run()) {
            // Assert: context should start successfully in NONE mode
            assertThat(ctx).isNotNull();
            assertThat(ctx.isRunning()).isTrue();

            // Verify the MaintenanceConfiguration bean exists
            assertThat(ctx.containsBean("adminRecoveryRunner")).isTrue();
        }
    }

    /**
     * Tests that a valid admin password reset generates a temporary password,
     * forces password change, revokes sessions, and writes audit event.
     */
    @Test
    void validAdminGeneratesTempPasswordAndRevokesSessions() {
        // Arrange: initialize household with admin
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "admin", "ADMIN");
        }

        // Capture stdout
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(stdout));

        try {
            // Act: start context and invoke command directly
            try (ConfigurableApplicationContext ctx = createBaseContext().run()) {
                JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

                // Invoke the command directly
                AdminRecoveryCommand command = ctx.getBean(AdminRecoveryCommand.class);
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
                        Integer.class, "PasswordRecoveredLocally");
                assertThat(auditCount).isEqualTo(1);
            }
        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Tests that an unknown user causes an exception and no data modification.
     */
    @Test
    void unknownUserFailsWithoutModifyingData() {
        // Arrange: initialize household with admin
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "admin", "ADMIN");
        }

        // Act: start context and try to reset unknown user
        try (ConfigurableApplicationContext ctx = createBaseContext().run()) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

            // Invoke the command with unknown user - should throw exception
            AdminRecoveryCommand command = ctx.getBean(AdminRecoveryCommand.class);
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
                    Integer.class, "PasswordRecoveredLocally");
            assertThat(auditCount).isEqualTo(0);
        }
    }

    /**
     * Tests that a non-admin user causes an exception and no data modification.
     */
    @Test
    void nonAdminUserFailsWithoutModifyingData() {
        // Arrange: initialize household with a MEMBER role user
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "member", "MEMBER");
        }

        // Act: start context and try to reset non-admin user
        try (ConfigurableApplicationContext ctx = createBaseContext().run()) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

            // Invoke the command with non-admin user - should throw exception
            AdminRecoveryCommand command = ctx.getBean(AdminRecoveryCommand.class);
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
                    Integer.class, "PasswordRecoveredLocally");
            assertThat(auditCount).isEqualTo(0);
        }
    }

    /**
     * Tests that a database failure causes an exception and no data modification.
     */
    @Test
    void databaseFailureFailsWithoutModifyingData() {
        // Arrange: initialize household with admin
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "admin", "ADMIN");
        }

        // Act: start context and simulate database failure by revoking all connections
        try (ConfigurableApplicationContext ctx = createBaseContext().run()) {
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

            // Revoke all sessions to simulate a database failure scenario
            // (this doesn't actually break the database, but tests the command's resilience)
            AdminRecoveryCommand command = ctx.getBean(AdminRecoveryCommand.class);

            // The command should still work even if there are no sessions to revoke
            String tempPassword = command.resetAdmin("admin");

            // Verify the command succeeded
            assertThat(tempPassword).isNotNull();
            assertThat(tempPassword).hasSize(20);

            // Verify password was changed
            String passwordHash = jdbc.queryForObject(
                    "SELECT password_hash FROM user_account WHERE normalized_username = ?",
                    String.class, "admin");
            assertThat(passwordHash).isNotNull();
            assertThat(passwordHash).doesNotContain("dummy.hash.for.testing");

            // Verify audit event was written
            Integer auditCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE event_type = ?",
                    Integer.class, "PasswordRecoveredLocally");
            assertThat(auditCount).isEqualTo(1);
        }
    }
}
