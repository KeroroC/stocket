package com.stocket.identity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

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

/**
 * Full-chain acceptance test for Phase 2 identity system.
 *
 * Each test is independent (per-test TRUNCATE) but the tests collectively
 * verify the entire identity lifecycle:
 *   1. Household init + auto-login
 *   2. Admin creates MEMBER via API
 *   3. VIEWER invite creation and acceptance
 *   4. Three-role permission matrix
 *   5. MEMBER password change revokes old sessions
 *   6. Admin reset VIEWER invalidates old sessions
 *   7. Audit table contains critical events and no sensitive values
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class IdentityAcceptanceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    /**
     * Provides a test-only write endpoint for three-role permission matrix testing.
     */
    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        TestWriteController testWriteController() {
            return new TestWriteController();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class TestWriteController {
        @PostMapping("/write")
        @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
        ResponseEntity<String> writeEndpoint() {
            return ResponseEntity.ok("OK");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE audit_log, household_member, user_session, member_invite, user_account, household CASCADE");
    }

    // ---- Step 1: Initialize household and verify auto-login ----

    @Test
    void initializeHouseholdReturnsAdminSessionAndPreventsDuplicateInit() throws Exception {
        // Initialize: should return 201 with session cookie and admin role
        String initResponse = mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"验收家庭","timezone":"Asia/Shanghai",
                                 "username":"Admin","displayName":"验收管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andExpect(jsonPath("$.username").value("Admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.accountId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID adminAccountId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(initResponse, "$.accountId").toString());
        assertThat(adminAccountId).isNotNull();

        // Auto-login cookie works for authenticated endpoints
        // Re-login to get a fresh cookie (init already happened)
        String adminCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Admin","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // Second init is rejected
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"Another","timezone":"Asia/Shanghai",
                                 "username":"Other","displayName":"Other","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_COMPLETED"));

        // Exactly one household, one account, one member
        assertThat(jdbc.queryForObject("SELECT count(*) FROM household", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM household_member", Integer.class)).isEqualTo(1);
    }

    // ---- Step 2: Admin creates MEMBER via API ----

    @Test
    void adminCreatesMemberWithTemporaryPasswordAndMemberCanLogin() throws Exception {
        String adminCookie = initializeAsAdmin();

        // Admin creates MEMBER
        String createResponse = mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Member1","displayName":"成员一","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("Member1"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String tempPassword = com.jayway.jsonpath.JsonPath.read(createResponse, "$.temporaryPassword").toString();
        assertThat(tempPassword).hasSize(20);

        // must_change_password is true in DB
        Boolean mustChange = jdbc.queryForObject(
                "SELECT must_change_password FROM user_account WHERE normalized_username = 'member1'",
                Boolean.class);
        assertThat(mustChange).isTrue();

        // Member can login with temporary password
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Member1","password":"%s"}
                                """.formatted(tempPassword)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"));

        // Temporary password is only shown once (GET member does not return it)
        UUID memberId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(createResponse, "$.id").toString());
        mockMvc.perform(get("/api/v1/admin/members/{memberId}", memberId)
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }

    // ---- Step 3: VIEWER invite creation and acceptance ----

    @Test
    void viewerInviteAndAcceptance() throws Exception {
        String adminCookie = initializeAsAdmin();

        // Create VIEWER invite
        String inviteResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inviteLink").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String inviteLink = com.jayway.jsonpath.JsonPath.read(inviteResponse, "$.inviteLink").toString();
        String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

        // Public status check (no auth needed)
        mockMvc.perform(get("/api/v1/invites/{token}/status", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.role").value("VIEWER"));

        // Accept invite
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Viewer1","displayName":"查看者一","password":"viewer-strong-password-123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNotEmpty())
                .andExpect(jsonPath("$.memberId").isNotEmpty());

        // Account and member created
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE normalized_username = 'viewer1'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM household_member m JOIN user_account a ON m.account_id = a.id WHERE a.normalized_username = 'viewer1'",
                Integer.class)).isEqualTo(1);

        // Viewer can login
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Viewer1","password":"viewer-strong-password-123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"));

        // Invite is now consumed
        mockMvc.perform(get("/api/v1/invites/{token}/status", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    // ---- Step 4: Three-role permission matrix ----

    @Test
    void threeRolePermissionMatrix() throws Exception {
        String adminCookie = initializeAsAdmin();
        String memberCookie = loginMemberCreatedViaApi(adminCookie, "Member1", "成员一", "MEMBER");
        String viewerCookie = loginViewerCreatedViaInvite(adminCookie, "Viewer1", "查看者一", "viewer-password-123");

        // All roles can GET /account
        for (String[] pair : new String[][]{
                {adminCookie, "ADMIN"}, {memberCookie, "MEMBER"}, {viewerCookie, "VIEWER"}
        }) {
            mockMvc.perform(get("/api/v1/account")
                            .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", pair[0])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(pair[1]));
        }

        // ADMIN and MEMBER can write
        mockMvc.perform(post("/api/v1/test/write")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/test/write")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie)))
                .andExpect(status().isOk());

        // VIEWER cannot write
        mockMvc.perform(post("/api/v1/test/write")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", viewerCookie)))
                .andExpect(status().isForbidden());

        // Only ADMIN can access admin endpoints
        mockMvc.perform(get("/api/v1/admin/members")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/members")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/members")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", viewerCookie)))
                .andExpect(status().isForbidden());
    }

    // ---- Step 5: MEMBER password change revokes all old sessions ----

    @Test
    void memberPasswordChangeRevokesAllOldSessions() throws Exception {
        String adminCookie = initializeAsAdmin();

        // Create MEMBER and get temp password
        String createResponse = mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Member1","displayName":"成员一","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String tempPassword = com.jayway.jsonpath.JsonPath.read(createResponse, "$.temporaryPassword").toString();
        UUID memberAccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'member1'", UUID.class);

        // Member logs in with temp password
        String session1 = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Member1","password":"%s"}
                                """.formatted(tempPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // Create a second session for the member (login again)
        String session2 = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Member1","password":"%s"}
                                """.formatted(tempPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // Verify 2 active sessions
        Integer sessionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, memberAccountId);
        assertThat(sessionsBefore).isEqualTo(2);

        // Member changes password (must use current temp password as oldPassword)
        String newCookie = mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", session1))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"member-new-secure-password-123"}
                                """.formatted(tempPassword)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // All old sessions revoked, only the new one remains
        Integer activeAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, memberAccountId);
        assertThat(activeAfter).isEqualTo(1);

        // Old cookies no longer work
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", session1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", session2)))
                .andExpect(status().isUnauthorized());

        // Verify must_change_password cleared through API (avoids JPA flush timing issues)
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", newCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    // ---- Step 6: Admin resets VIEWER password, old sessions invalidated ----

    @Test
    void adminResetViewerPasswordInvalidatesOldSessions() throws Exception {
        String adminCookie = initializeAsAdmin();
        String viewerCookie = loginViewerCreatedViaInvite(adminCookie, "Viewer1", "查看者一", "viewer-password-123");

        UUID viewerAccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'viewer1'", UUID.class);
        UUID viewerMemberId = jdbc.queryForObject(
                "SELECT m.id FROM household_member m WHERE m.account_id = ?",
                UUID.class, viewerAccountId);

        // Viewer has an active session
        Integer sessionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, viewerAccountId);
        assertThat(sessionsBefore).isEqualTo(1);

        // Admin resets viewer password
        String resetResponse = mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", viewerMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String newTempPassword = com.jayway.jsonpath.JsonPath.read(resetResponse, "$.temporaryPassword").toString();
        assertThat(newTempPassword).hasSize(20);

        // Old session revoked
        Integer activeAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, viewerAccountId);
        assertThat(activeAfter).isEqualTo(0);

        // must_change_password is true
        Boolean mustChange = jdbc.queryForObject(
                "SELECT must_change_password FROM user_account WHERE id = ?",
                Boolean.class, viewerAccountId);
        assertThat(mustChange).isTrue();

        // Old cookie no longer works
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", viewerCookie)))
                .andExpect(status().isUnauthorized());

        // Login with new temp password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Viewer1","password":"%s"}
                                """.formatted(newTempPassword)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"));
    }

    // ---- Step 7: Audit verification ----

    @Test
    void auditTableContainsCriticalEventsAndNoSensitiveValues() throws Exception {
        String adminCookie = initializeAsAdmin();

        // Create MEMBER to generate MEMBER_CREATED audit event
        String memberCreateResponse = mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Member1","displayName":"成员一","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String memberTempPassword = com.jayway.jsonpath.JsonPath.read(memberCreateResponse, "$.temporaryPassword").toString();

        // Member login + password change (generates LOGIN_SUCCEEDED, PASSWORD_CHANGED, SESSION_REVOKED)
        String memberCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Member1","password":"%s"}
                                """.formatted(memberTempPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        String newMemberCookie = mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"member-new-secure-password-123"}
                                """.formatted(memberTempPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // Create a second member session, then revoke it to generate SessionRevoked event
        String memberCookie2 = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Member1","password":"member-new-secure-password-123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        mockMvc.perform(delete("/api/v1/account/sessions/others")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", newMemberCookie)))
                .andExpect(status().isNoContent());

        // VIEWER invite + accept (generates INVITE_CREATED, INVITE_ACCEPTED)
        String inviteResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inviteLink = com.jayway.jsonpath.JsonPath.read(inviteResponse, "$.inviteLink").toString();
        String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Viewer1","displayName":"查看者一","password":"viewer-strong-password-123"}
                                """))
                .andExpect(status().isCreated());

        // Admin resets viewer password (generates PASSWORD_RESET_BY_ADMIN)
        UUID viewerMemberId = jdbc.queryForObject(
                "SELECT m.id FROM household_member m JOIN user_account a ON m.account_id = a.id WHERE a.normalized_username = 'viewer1'",
                UUID.class);
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", viewerMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());

        // Logout admin to generate LOGGED_OUT event
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        // ---- Verify critical audit event types exist ----
        List<String> requiredEvents = List.of(
                "HouseholdInitialized",
                "LoginSucceeded",
                "MemberCreated",
                "InviteCreated",
                "InviteAccepted",
                "PasswordChanged",
                "PasswordResetByAdmin",
                "SessionRevoked",
                "LoggedOut"
        );

        for (String eventType : requiredEvents) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_log WHERE event_type = ?",
                    Integer.class, eventType);
            assertThat(count)
                    .as("Audit event '%s' should exist", eventType)
                    .isGreaterThanOrEqualTo(1);
        }

        // ---- Scan all audit_log details for sensitive values ----
        List<Map<String, Object>> allLogs = jdbc.queryForList(
                "SELECT event_type, details::text AS details_text FROM audit_log");

        for (Map<String, Object> row : allLogs) {
            String eventType = (String) row.get("event_type");
            String detailsText = getDetailsAsText(row);

            if (detailsText == null || detailsText.isBlank()) {
                continue;
            }

            String lower = detailsText.toLowerCase();

            // Must not contain any raw passwords
            assertThat(lower)
                    .as("Audit '%s' must not leak raw password", eventType)
                    .doesNotContain("correct horse battery staple");
            assertThat(lower)
                    .as("Audit '%s' must not leak raw password", eventType)
                    .doesNotContain("member-new-secure-password");
            assertThat(lower)
                    .as("Audit '%s' must not leak raw password", eventType)
                    .doesNotContain("viewer-strong-password");
            assertThat(lower)
                    .as("Audit '%s' must not leak raw password", eventType)
                    .doesNotContain("viewer-password");

            // Must not contain raw token keywords
            assertThat(lower)
                    .as("Audit '%s' must not contain 'temporarypassword'", eventType)
                    .doesNotContain("temporarypassword");
            assertThat(lower)
                    .as("Audit '%s' must not contain 'token_value'", eventType)
                    .doesNotContain("token_value");
        }
    }

    // ---- Helpers ----

    /**
     * Initializes the household and returns the admin session cookie.
     */
    private String initializeAsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"验收家庭","timezone":"Asia/Shanghai",
                                 "username":"Admin","displayName":"验收管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(cookie().exists("STOCKET_SESSION"));

        // Login to get a fresh session cookie
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Admin","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();
    }

    /**
     * Creates a MEMBER via admin API, logs in with the temp password,
     * changes password to clear must_change_password, and returns the session cookie.
     */
    private String loginMemberCreatedViaApi(String adminCookie, String username,
                                             String displayName, String role) throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"%s","role":"%s"}
                                """.formatted(username, displayName, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String tempPassword = com.jayway.jsonpath.JsonPath.read(createResponse, "$.temporaryPassword").toString();

        String tempCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, tempPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // Change password to clear must_change_password
        String newPassword = username.toLowerCase() + "-secure-password-123";
        return mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", tempCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"%s","newPassword":"%s"}
                                """.formatted(tempPassword, newPassword)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();
    }

    /**
     * Creates a VIEWER via invite flow, logs in, and returns the session cookie.
     */
    private String loginViewerCreatedViaInvite(String adminCookie, String username,
                                                String displayName, String password) throws Exception {
        // Create invite
        String inviteResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inviteLink = com.jayway.jsonpath.JsonPath.read(inviteResponse, "$.inviteLink").toString();
        String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

        // Accept invite
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"%s","password":"%s"}
                                """.formatted(username, displayName, password)))
                .andExpect(status().isCreated());

        // Login
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();
    }

    private String getDetailsAsText(Map<String, Object> row) {
        Object details = row.get("details_text");
        if (details instanceof String s) {
            return s;
        }
        if (details instanceof org.postgresql.util.PGobject pgo) {
            return pgo.getValue();
        }
        return details != null ? details.toString() : null;
    }
}
