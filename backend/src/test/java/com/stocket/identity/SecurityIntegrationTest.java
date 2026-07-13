package com.stocket.identity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.SessionService;
import com.stocket.identity.internal.authentication.TokenHasher;
import com.stocket.identity.internal.security.IdentityPrincipal;

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
class SecurityIntegrationTest {

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

    @Autowired
    private SessionService sessionService;

    @Autowired
    private CurrentHouseholdProvider currentHouseholdProvider;

    @BeforeEach
    void cleanDatabase() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");
    }

    @Test
    void publicSystemEndpointIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("stocket"));
    }

    @Test
    void protectedEndpointWithoutCookieReturns401WithProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void forgedCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", "forged-token-value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void authenticatedMemberAccessingAdminEndpointReturns403() throws Exception {
        // First, create an admin account via setup
        String adminCookie = initializeAndGetCookie();

        // Create a VIEWER account directly in the database
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID viewerAccountId = UUID.randomUUID();
        UUID householdId = jdbc.queryForObject(
                "SELECT id FROM household LIMIT 1", UUID.class);
        String viewerPasswordHash = passwordEncoder.encode("viewer-password");

        jdbc.update("""
                INSERT INTO user_account (id, username, normalized_username, display_name,
                password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                VALUES (?, 'Viewer', 'viewer', '查看者', ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, viewerAccountId, viewerPasswordHash);

        jdbc.update("""
                INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
                VALUES (?, ?, ?, 'VIEWER', now(), now())
                """, UUID.randomUUID(), householdId, viewerAccountId);

        // Create a session for the viewer directly in the database
        String viewerToken = secureValueGenerator.generateToken();
        String viewerTokenHash = tokenHasher.sha256(viewerToken);
        Instant now = Instant.now();

        jdbc.update("""
                INSERT INTO user_session (id, account_id, token_hash, created_at, last_seen_at,
                idle_expires_at, absolute_expires_at, user_agent, source_address)
                VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz, 'test', '127.0.0.1')
                """,
                UUID.randomUUID(),
                viewerAccountId,
                viewerTokenHash,
                now.toString(),
                now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(),
                now.plus(90, ChronoUnit.DAYS).toString());

        // Try to access admin endpoint with VIEWER role - should get 403
        mockMvc.perform(get("/api/v1/admin/test")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", viewerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void writeRequestWithoutCsrfReturns403() throws Exception {
        // First, create an account and get session cookie
        String cookie = initializeAndGetCookie();

        // Try to POST without CSRF token
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", cookie)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void validCookiePassesAuthenticationFilter() throws Exception {
        // First, create an account and get session cookie
        String cookie = initializeAndGetCookie();

        // Access a protected endpoint with valid cookie - 200 means auth passed
        // (401 would mean auth failed)
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", cookie)))
                .andExpect(status().isOk());
    }

    @Test
    void sessionWithoutHouseholdMembershipIsRejected() throws Exception {
        initializeAndGetCookie();

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID accountId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode("orphan-password");
        jdbc.update("""
                INSERT INTO user_account (id, username, normalized_username, display_name,
                password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                VALUES (?, 'Orphan', 'orphan', '无家庭账号', ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, passwordHash);

        String token = secureValueGenerator.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO user_session (id, account_id, token_hash, created_at, last_seen_at,
                idle_expires_at, absolute_expires_at, user_agent, source_address)
                VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz, 'test', '127.0.0.1')
                """,
                UUID.randomUUID(),
                accountId,
                tokenHasher.sha256(token),
                now.toString(),
                now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(),
                now.plus(90, ChronoUnit.DAYS).toString());

        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void persistedMembershipPopulatesCurrentHouseholdContext() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID householdId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String token = secureValueGenerator.generateToken();
        Instant now = Instant.now();

        jdbc.update("insert into household(id, singleton_key, name, timezone) values (?, 1, '家', 'Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into user_account(id, username, normalized_username, display_name, password_hash,
                    status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                values (?, 'Member', 'member', '成员', ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, passwordEncoder.encode("member-password"));
        jdbc.update("""
                insert into household_member(id, household_id, account_id, role, created_at, updated_at)
                values (?, ?, ?, 'MEMBER', now(), now())
                """, memberId, householdId, accountId);
        jdbc.update("""
                insert into user_session(id, account_id, token_hash, created_at, last_seen_at,
                    idle_expires_at, absolute_expires_at, user_agent, source_address)
                values (?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz,
                    'test', '127.0.0.1')
                """, UUID.randomUUID(), accountId, tokenHasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());

        IdentityPrincipal principal = sessionService.authenticate(token, now).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
        try {
            CurrentHousehold current = currentHouseholdProvider.requireCurrent();
            org.assertj.core.api.Assertions.assertThat(current.householdId()).isEqualTo(householdId);
            org.assertj.core.api.Assertions.assertThat(current.memberId()).isEqualTo(memberId);
            org.assertj.core.api.Assertions.assertThat(current.role()).isEqualTo(IdentityRole.MEMBER);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void csrfEndpointReturnsTokenWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").exists())
                .andExpect(jsonPath("$.parameterName").exists())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void sessionIsStoredInDatabase() throws Exception {
        // Create a session
        String cookie = initializeAndGetCookie();

        // Verify the session works in the current context
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", cookie)))
                .andExpect(status().isOk());

        // Verify the session is stored in the database
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer sessionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE revoked_at IS NULL",
                Integer.class);

        org.assertj.core.api.Assertions.assertThat(sessionCount).isEqualTo(1);
    }

    @Test
    void passwordChangeRequiredBlocksNonExemptPaths() throws Exception {
        // Create an account with must_change_password=true
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID accountId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode("test-password");

        // First initialize to create household
        initializeAndGetCookie();
        householdId = jdbc.queryForObject("SELECT id FROM household LIMIT 1", UUID.class);

        // Create user with must_change_password=true
        jdbc.update("""
                INSERT INTO user_account (id, username, normalized_username, display_name,
                password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                VALUES (?, 'TestUser', 'testuser', '测试用户', ?, 'ACTIVE', true, now(), now(), now(), 0)
                """, accountId, passwordHash);

        jdbc.update("""
                INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
                VALUES (?, ?, ?, 'MEMBER', now(), now())
                """, UUID.randomUUID(), householdId, accountId);

        // Create session for the user
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

        // Non-exempt paths should return 403 with PASSWORD_CHANGE_REQUIRED
        mockMvc.perform(get("/api/v1/test/nonexempt")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void passwordChangeRequiredAllowsExemptPaths() throws Exception {
        // Create an account with must_change_password=true
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID accountId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode("test-password");

        // First initialize to create household
        initializeAndGetCookie();
        householdId = jdbc.queryForObject("SELECT id FROM household LIMIT 1", UUID.class);

        // Create user with must_change_password=true
        jdbc.update("""
                INSERT INTO user_account (id, username, normalized_username, display_name,
                password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                VALUES (?, 'TestUser', 'testuser', '测试用户', ?, 'ACTIVE', true, now(), now(), now(), 0)
                """, accountId, passwordHash);

        jdbc.update("""
                INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
                VALUES (?, ?, ?, 'MEMBER', now(), now())
                """, UUID.randomUUID(), householdId, accountId);

        // Create session for the user
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

        // Exempt paths should be accessible
        mockMvc.perform(get("/api/v1/auth/csrf")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/system")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", token)))
                .andExpect(status().isOk());

        // Account endpoints should also be accessible for mustChangePassword=true users
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", token))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"test-password","newPassword":"new correct horse battery staple"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void passwordChangeRequiredBlocksAdminWithMustChangePassword() throws Exception {
        // Create an ADMIN account with must_change_password=true
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID accountId = UUID.randomUUID();
        UUID householdId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode("test-password");

        // First initialize to create household
        initializeAndGetCookie();
        householdId = jdbc.queryForObject("SELECT id FROM household LIMIT 1", UUID.class);

        // Create ADMIN user with must_change_password=true
        jdbc.update("""
                INSERT INTO user_account (id, username, normalized_username, display_name,
                password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                VALUES (?, 'AdminUser', 'adminuser', '管理员', ?, 'ACTIVE', true, now(), now(), now(), 0)
                """, accountId, passwordHash);

        jdbc.update("""
                INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
                VALUES (?, ?, ?, 'ADMIN', now(), now())
                """, UUID.randomUUID(), householdId, accountId);

        // Create session for the admin
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

        // ADMIN with must_change_password should still be blocked on admin paths
        mockMvc.perform(get("/api/v1/admin/test")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    private String initializeAndGetCookie() throws Exception {
        return mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andReturn()
                .getResponse()
                .getCookie("STOCKET_SESSION")
                .getValue();
    }
}
