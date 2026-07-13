package com.stocket.identity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
class AccountIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecureValueGenerator secureValueGenerator;

    @Autowired
    private TokenHasher tokenHasher;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");
    }

    // ---- GET /account ----

    @Test
    void getCurrentAccountReturnsProfileWithRole() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.username").value("Owner"))
                .andExpect(jsonPath("$.displayName").value("管理员"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    // ---- PATCH /account/profile ----

    @Test
    void updateProfileChangesDisplayNameAndEmail() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        mockMvc.perform(patch("/api/v1/account/profile")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"displayName":"新名称","email":"test@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("新名称"))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        // Verify persisted
        String displayName = jdbc.queryForObject(
                "SELECT display_name FROM user_account WHERE normalized_username = 'owner'",
                String.class);
        assertThat(displayName).isEqualTo("新名称");
    }

    @Test
    void updateProfileWithOnlyDisplayNameClearsEmail() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        // First set an email
        mockMvc.perform(patch("/api/v1/account/profile")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"displayName":"管理员","email":"old@example.com"}
                                """))
                .andExpect(status().isOk());

        // Update without email - email should be cleared (null)
        mockMvc.perform(patch("/api/v1/account/profile")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"displayName":"管理员"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    // ---- POST /account/password ----

    @Test
    void changePasswordWithWrongOldPasswordReturns400() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"wrong old password","newPassword":"new correct horse battery staple"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void changePasswordWithWeakNewPasswordReturns400() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"correct horse battery staple","newPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
    }

    @Test
    void successfulPasswordChangeRevokesAllSessionsAndCreatesNewOne() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        // Create a second session for the same account
        UUID accountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        String secondToken = createSessionForAccount(accountId);

        // Verify we have 2 active sessions
        Integer sessionCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, accountId);
        assertThat(sessionCountBefore).isEqualTo(2);

        // Change password
        String newPassword = "new correct horse battery staple";
        mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(rawPassword, newPassword)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"));

        // All old sessions should be revoked
        Integer activeSessionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, accountId);
        assertThat(activeSessionsAfter).isEqualTo(1); // Only the new session

        // The new session should work with the new password
        // must_change_password should be false
        Boolean mustChange = jdbc.queryForObject(
                "SELECT must_change_password FROM user_account WHERE id = ?",
                Boolean.class, accountId);
        assertThat(mustChange).isFalse();
    }

    // ---- GET /account/sessions ----

    @Test
    void listSessionsReturnsOnlySafeFields() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        // Create a second session
        UUID accountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        createSessionForAccount(accountId);

        mockMvc.perform(get("/api/v1/account/sessions")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].current").isBoolean())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].lastSeenAt").isNotEmpty())
                .andExpect(jsonPath("$[0].absoluteExpiresAt").isNotEmpty())
                // Must never expose tokenHash
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());
    }

    @Test
    void listSessionsMarksCurrentSession() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        mockMvc.perform(get("/api/v1/account/sessions")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    // ---- DELETE /account/sessions/{sessionId} ----

    @Test
    void revokeOwnSessionReturns204() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        // Create a second session to revoke
        UUID accountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        String secondToken = createSessionForAccount(accountId);
        String secondTokenHash = tokenHasher.sha256(secondToken);
        UUID secondSessionId = jdbc.queryForObject(
                "SELECT id FROM user_session WHERE token_hash = ?", UUID.class, secondTokenHash);

        mockMvc.perform(delete("/api/v1/account/sessions/{sessionId}", secondSessionId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent());

        // Verify session is revoked
        Instant revokedAt = jdbc.queryForObject(
                "SELECT revoked_at FROM user_session WHERE id = ?",
                Instant.class, secondSessionId);
        assertThat(revokedAt).isNotNull();
    }

    @Test
    void revokeOthersSessionReturns404() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        // Create a second account and session
        UUID otherAccountId = createOtherAccount("OtherUser", "otheruser", "other-password-here");
        String otherToken = createSessionForAccount(otherAccountId);
        String otherTokenHash = tokenHasher.sha256(otherToken);
        UUID otherSessionId = jdbc.queryForObject(
                "SELECT id FROM user_session WHERE token_hash = ?", UUID.class, otherTokenHash);

        // Try to revoke other user's session - should fail
        mockMvc.perform(delete("/api/v1/account/sessions/{sessionId}", otherSessionId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNotFound());

        // Verify session is NOT revoked
        Instant revokedAt = jdbc.queryForObject(
                "SELECT revoked_at FROM user_session WHERE id = ?",
                Instant.class, otherSessionId);
        assertThat(revokedAt).isNull();
    }

    // ---- DELETE /account/sessions/others ----

    @Test
    void revokeOtherSessionsRevokesAllExceptCurrent() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        // Create two more sessions for the same account
        UUID accountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        createSessionForAccount(accountId);
        createSessionForAccount(accountId);

        // Verify we have 3 active sessions
        Integer sessionCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, accountId);
        assertThat(sessionCountBefore).isEqualTo(3);

        // Revoke all other sessions
        mockMvc.perform(delete("/api/v1/account/sessions/others")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent());

        // Only current session should remain active
        Integer activeSessionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, accountId);
        assertThat(activeSessionsAfter).isEqualTo(1);
    }

    @Test
    void revokeOtherSessionsIsIdempotentWhenNoOtherSessions() throws Exception {
        String rawPassword = "correct horse battery staple";
        String sessionCookie = loginAsAdmin(rawPassword);

        // Only 1 session exists (the current one)
        mockMvc.perform(delete("/api/v1/account/sessions/others")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent());
    }

    // ---- Helpers ----

    private String loginAsAdmin(String rawPassword) throws Exception {
        // Initialize to create household and admin
        String sessionCookie = mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"%s"}
                                """.formatted(rawPassword)))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andReturn()
                .getResponse()
                .getCookie("STOCKET_SESSION")
                .getValue();

        return sessionCookie;
    }

    private UUID createOtherAccount(String username, String normalizedUsername, String rawPassword) {
        UUID accountId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(rawPassword);
        UUID householdId = jdbc.queryForObject("SELECT id FROM household LIMIT 1", UUID.class);

        jdbc.update("""
                INSERT INTO user_account (id, username, normalized_username, display_name,
                password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, username, normalizedUsername, username, passwordHash);

        jdbc.update("""
                INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
                VALUES (?, ?, ?, 'MEMBER', now(), now())
                """, UUID.randomUUID(), householdId, accountId);

        return accountId;
    }

    private String createSessionForAccount(UUID accountId) {
        String token = secureValueGenerator.generateToken();
        String tokenHash = tokenHasher.sha256(token);
        Instant now = Instant.now();

        jdbc.update("""
                INSERT INTO user_session (id, account_id, token_hash, created_at, last_seen_at,
                idle_expires_at, absolute_expires_at, user_agent, source_address)
                VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz, 'test', '127.0.0.1')
                """,
                UUID.randomUUID(),
                accountId,
                tokenHash,
                now.toString(),
                now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(),
                now.plus(90, ChronoUnit.DAYS).toString());

        return token;
    }
}
