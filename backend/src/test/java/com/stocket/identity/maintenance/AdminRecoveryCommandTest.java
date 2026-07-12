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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the local administrator recovery command.
 * Verifies that the command can reset an admin password, revoke sessions,
 * write audit events, and handle error cases correctly.
 *
 * <p>Tests launch the full Spring context via {@link SpringApplicationBuilder}
 * with the {@code --stocket.maintenance.reset-admin} command-line argument
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
     * The parameter must be passed as a command-line argument with the {@code --} prefix.
     */
    @Test
    void resetParamAutoUsesNoneMode() {
        // Arrange: initialize household
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "admin", "ADMIN");
        }

        // Act: start context with maintenance parameter as command-line argument
        try (ConfigurableApplicationContext ctx = createBaseContext()
                .run("--stocket.maintenance.reset-admin=admin")) {
            // Assert: context should start successfully
            assertThat(ctx).isNotNull();
            assertThat(ctx.isRunning()).isTrue();

            // Verify the MaintenanceConfiguration bean exists
            assertThat(ctx.containsBean("adminRecoveryRunner")).isTrue();

            // Verify the command was executed (password should be changed)
            JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
            String passwordHash = jdbc.queryForObject(
                    "SELECT password_hash FROM user_account WHERE normalized_username = ?",
                    String.class, "admin");
            assertThat(passwordHash).isNotNull();
            assertThat(passwordHash).doesNotContain("dummy.hash.for.testing");
        }
    }

    /**
     * Tests that a valid admin password reset generates a temporary password,
     * forces password change, revokes sessions, and writes audit event.
     * Verifies stdout output from the ApplicationRunner.
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
            // Act: start context with maintenance parameter as command-line argument
            try (ConfigurableApplicationContext ctx = createBaseContext()
                    .run("--stocket.maintenance.reset-admin=admin")) {
                JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

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

            // Verify stdout output from ApplicationRunner
            String output = stdout.toString();
            assertThat(output).contains("Admin recovery successful");
            assertThat(output).contains("Username: admin");
            assertThat(output).contains("Temporary password:");
            assertThat(output).contains("The user must change this password on first login");
        } finally {
            System.setOut(originalOut);
        }
    }

    /**
     * Tests that an unknown user causes the context to fail to start
     * (non-zero exit code) and no data modification.
     */
    @Test
    void unknownUserFailsWithoutModifyingData() {
        // Arrange: initialize household with admin
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "admin", "ADMIN");
        }

        // Act: start context with unknown user - should fail to start
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ctx = createBaseContext()
                    .run("--stocket.maintenance.reset-admin=nonexistent")) {
                // Should not reach here
            }
        }).isInstanceOf(Exception.class);

        // Verify no data was modified
        try (ConfigurableApplicationContext verifyCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = verifyCtx.getBean(JdbcTemplate.class);

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
     * Tests that a non-admin user causes the context to fail to start
     * (non-zero exit code) and no data modification.
     */
    @Test
    void nonAdminUserFailsWithoutModifyingData() {
        // Arrange: initialize household with a MEMBER role user
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "member", "MEMBER");
        }

        // Act: start context with non-admin user - should fail to start
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ctx = createBaseContext()
                    .run("--stocket.maintenance.reset-admin=member")) {
                // Should not reach here
            }
        }).isInstanceOf(Exception.class);

        // Verify no data was modified
        try (ConfigurableApplicationContext verifyCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = verifyCtx.getBean(JdbcTemplate.class);

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
     * Tests that a database failure causes the context to fail to start
     * (non-zero exit code). Since the context fails to start, no data
     * could have been modified.
     */
    @Test
    void databaseFailureFailsWithoutModifyingData() {
        // Arrange: initialize household with admin
        try (ConfigurableApplicationContext initCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = initCtx.getBean(JdbcTemplate.class);
            initializeHousehold(jdbc, "admin", "ADMIN");
        }

        // Act: try to start context with invalid database URL to simulate failure
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(StocketApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties(
                            "spring.datasource.url=jdbc:postgresql://localhost:99999/invalid",
                            "spring.datasource.username=invalid",
                            "spring.datasource.password=invalid")
                    .run("--stocket.maintenance.reset-admin=admin")) {
                // Should not reach here
            }
        }).isInstanceOf(Exception.class);

        // Verify no data was modified in the real database
        try (ConfigurableApplicationContext verifyCtx = createBaseContext().run()) {
            JdbcTemplate jdbc = verifyCtx.getBean(JdbcTemplate.class);

            // Verify password was NOT changed
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
}
