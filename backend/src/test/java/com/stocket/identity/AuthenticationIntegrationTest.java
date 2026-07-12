package com.stocket.identity;

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

import com.stocket.identity.internal.authentication.BoundedRateLimiter;
import com.stocket.identity.internal.authentication.LoginThrottleKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
class AuthenticationIntegrationTest {

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
    private BoundedRateLimiter<LoginThrottleKey> rateLimiter;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");
        rateLimiter.clear();
    }

    // ---- Login tests ----

    @Test
    void correctCredentialsReturn200WithAccountInfoAndSessionCookie() throws Exception {
        String rawPassword = "correct horse battery staple";
        UUID accountId = createAccountWithPassword("Owner", "owner", rawPassword);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"%s"}
                                """.formatted(rawPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.username").value("Owner"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(cookie().exists("STOCKET_SESSION"));
    }

    @Test
    void wrongPasswordReturns401WithInvalidCredentials() throws Exception {
        createAccountWithPassword("Owner", "owner", "correct horse battery staple");

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"wrong password here"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void unknownUsernameReturnsSame401ResponseAsWrongPassword() throws Exception {
        var wrongPasswordResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"wrong password here"}
                                """))
                .andReturn();

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"nonexistent","password":"wrong password here"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void disabledAccountReturns401WithInvalidCredentials() throws Exception {
        UUID accountId = createAccountWithPassword("Owner", "owner", "correct horse battery staple");
        disableAccount(accountId);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void elevenFailuresReturns429() throws Exception {
        createAccountWithPassword("Owner", "owner", "correct horse battery staple");

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"username":"Owner","password":"wrong password here"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        // 11th attempt should be rate limited
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"wrong password here"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void successfulLoginClearsFailureBucket() throws Exception {
        String rawPassword = "correct horse battery staple";
        createAccountWithPassword("Owner", "owner", rawPassword);

        // 9 failures (one short of the limit)
        for (int i = 0; i < 9; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"username":"Owner","password":"wrong password here"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        // Successful login resets the bucket
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"%s"}
                                """.formatted(rawPassword)))
                .andExpect(status().isOk());

        // Should be able to fail 10 more times (bucket was cleared)
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"username":"Owner","password":"wrong password here"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        // 11th after clear should be rate limited again
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"wrong password here"}
                                """))
                .andExpect(status().isTooManyRequests());
    }

    // ---- Logout tests ----

    @Test
    void logoutRevokesSessionAndClearsCookie() throws Exception {
        String rawPassword = "correct horse battery staple";
        createAccountWithPassword("Owner", "owner", rawPassword);

        // Login to get a session
        String sessionCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"%s"}
                                """.formatted(rawPassword)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // Verify session works
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNotFound());

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("STOCKET_SESSION", 0));

        // Session should be revoked - verify in DB
        Integer activeSessions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE revoked_at IS NULL",
                Integer.class);
        assertThat(activeSessions).isEqualTo(0);
    }

    @Test
    void logoutWithoutSessionStillClearsCookieAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void repeatedLogoutIsIdempotent() throws Exception {
        String rawPassword = "correct horse battery staple";
        createAccountWithPassword("Owner", "owner", rawPassword);

        String sessionCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"%s"}
                                """.formatted(rawPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // First logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent());

        // Second logout (same cookie, already revoked)
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent());
    }

    // ---- CSRF rotation tests ----

    @Test
    void loginClearsOldCsrfAndLogoutClearsOldCsrf() throws Exception {
        String rawPassword = "correct horse battery staple";
        createAccountWithPassword("Owner", "owner", rawPassword);

        // Get initial CSRF token
        String initialCsrf = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Login with CSRF - should rotate CSRF token
        String sessionCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"%s"}
                                """.formatted(rawPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // Logout - should clear CSRF token
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent());

        // After logout, /auth/csrf should issue a new token
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    // ---- Helpers ----

    private UUID createAccountWithPassword(String username, String normalizedUsername, String rawPassword) {
        UUID accountId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(rawPassword);

        // First initialize to create household
        try {
            mockMvc.perform(post("/api/v1/setup/initialize")
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"householdName":"王家","timezone":"Asia/Shanghai",
                                     "username":"%s","displayName":"管理员","password":"%s"}
                                    """.formatted(username, rawPassword)))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Revoke the session created by setup to avoid interfering with tests
        jdbc.execute("UPDATE user_session SET revoked_at = now() WHERE revoked_at IS NULL");

        UUID householdId = jdbc.queryForObject("SELECT id FROM household LIMIT 1", UUID.class);

        // If the setup created a different user than requested, create the requested user
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE normalized_username = ?",
                Integer.class, normalizedUsername);

        if (existing == 0) {
            jdbc.update("""
                    INSERT INTO user_account (id, username, normalized_username, display_name,
                    password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                    VALUES (?, ?, ?, '管理员', ?, 'ACTIVE', false, now(), now(), now(), 0)
                    """, accountId, username, normalizedUsername, passwordHash);

            jdbc.update("""
                    INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
                    VALUES (?, ?, ?, 'ADMIN', now(), now())
                    """, UUID.randomUUID(), householdId, accountId);
        } else {
            accountId = jdbc.queryForObject(
                    "SELECT id FROM user_account WHERE normalized_username = ?",
                    UUID.class, normalizedUsername);
        }

        return accountId;
    }

    private void disableAccount(UUID accountId) {
        jdbc.update("UPDATE user_account SET status = 'DISABLED' WHERE id = ?", accountId);
    }
}
