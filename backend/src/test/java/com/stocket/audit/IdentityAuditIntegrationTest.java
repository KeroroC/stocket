package com.stocket.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the identity audit module.
 * Exercises all audit event types and asserts correct persistence
 * in the audit_log table, including sensitive data filtering.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class IdentityAuditIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock clock() {
            return new MutableClock(Instant.now());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE audit_log, household_member, user_session, member_invite, user_account, household CASCADE");
    }

    // ---- Setup / Initialization events ----

    @Test
    void initializationPublishesLoginAuditEvent() throws Exception {
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated());

        // There should be a LOGIN SUCCESS event from the session created during setup
        List<Map<String, Object>> rows = queryAuditLogs("LOGIN");
        assertThat(rows).isNotEmpty();

        Map<String, Object> loginEvent = rows.getFirst();
        assertThat(loginEvent.get("outcome")).isEqualTo("SUCCESS");
        assertThat(loginEvent.get("subject_type")).isEqualTo("USER_ACCOUNT");
        assertThat(loginEvent.get("source")).isNotNull();
    }

    // ---- Login success ----

    @Test
    void successfulLoginCreatesAuditEvent() throws Exception {
        initializeHousehold();

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk());

        List<Map<String, Object>> successEvents = queryAuditLogs("LOGIN").stream()
                .filter(r -> "SUCCESS".equals(r.get("outcome")))
                .toList();
        assertThat(successEvents).isNotEmpty();

        Map<String, Object> event = successEvents.getFirst();
        assertThat(event.get("actor_account_id")).isNotNull();
        assertThat(event.get("subject_type")).isEqualTo("USER_ACCOUNT");
    }

    // ---- Login failure ----

    @Test
    void failedLoginCreatesAuditEventWithUsernameFingerprint() throws Exception {
        initializeHousehold();

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"wrong password"}
                                """))
                .andExpect(status().isUnauthorized());

        List<Map<String, Object>> failureEvents = queryAuditLogs("LOGIN").stream()
                .filter(r -> "FAILURE".equals(r.get("outcome")))
                .toList();
        assertThat(failureEvents).isNotEmpty();

        Map<String, Object> event = failureEvents.getFirst();
        // Failed login: actor_account_id is null
        assertThat(event.get("actor_account_id")).isNull();

        // Details should contain a username fingerprint
        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).containsKey("usernameFingerprint");
        assertThat(details.get("usernameFingerprint").toString()).isNotEmpty();
        // Must not contain plaintext username
        assertThat(details.get("usernameFingerprint").toString()).isNotEqualTo("owner");
        assertThat(details.get("usernameFingerprint").toString()).isNotEqualTo("Owner");
    }

    // ---- Logout ----

    @Test
    void logoutCreatesAuditEvent() throws Exception {
        initializeHousehold();
        String sessionCookie = loginAs("Owner", "correct horse battery staple");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = queryAuditLogs("LOGOUT");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isNotNull();
    }

    // ---- Member created ----

    @Test
    void memberCreationCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();

        mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"NewUser","displayName":"新用户","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = queryAuditLogs("MEMBER_CREATED");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("subject_type")).isEqualTo("HOUSEHOLD_MEMBER");

        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).containsKey("targetAccountId");
        assertThat(details).containsKey("role");
        assertThat(details.get("role")).isEqualTo("MEMBER");
    }

    // ---- Member role changed ----

    @Test
    void memberRoleChangeCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();
        UUID memberId = createMemberViaApi(adminCookie, "RoleUser", "角色用户", "MEMBER");

        mockMvc.perform(patch("/api/v1/admin/members/{memberId}/role", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = queryAuditLogs("MEMBER_ROLE_CHANGED");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");

        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details.get("oldRole")).isEqualTo("MEMBER");
        assertThat(details.get("newRole")).isEqualTo("VIEWER");
    }

    // ---- Member disabled ----

    @Test
    void memberDisableCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();
        UUID memberId = createMemberViaApi(adminCookie, "DisableUser", "禁用用户", "MEMBER");

        // Create a second admin to allow disabling
        createMemberViaApi(adminCookie, "SecondAdmin", "第二管理员", "ADMIN");

        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = queryAuditLogs("MEMBER_DISABLED");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
    }

    // ---- Password reset by admin ----

    @Test
    void adminPasswordResetCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();
        UUID memberId = createMemberViaApi(adminCookie, "ResetUser", "重置用户", "MEMBER");

        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = queryAuditLogs("PASSWORD_RESET_BY_ADMIN");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isNotNull();

        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).containsKey("targetAccountId");
    }

    // ---- Password changed (self) ----

    @Test
    void selfPasswordChangeCreatesAuditEvent() throws Exception {
        String sessionCookie = loginAsAdmin();

        UUID adminAccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);

        mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"correct horse battery staple",
                                 "newPassword":"new correct horse battery staple"}
                                """))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = queryAuditLogs("PASSWORD_CHANGED");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isEqualTo(adminAccountId);
    }

    // ---- Invite created ----

    @Test
    void inviteCreationCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();

        mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = queryAuditLogs("INVITE_CREATED");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");

        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).containsKey("inviteId");
        assertThat(details).containsKey("role");
    }

    // ---- Invite accepted ----

    @Test
    void inviteAcceptanceCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();

        String createResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inviteLink = com.jayway.jsonpath.JsonPath.read(createResponse, "$.inviteLink").toString();
        String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"InvitedUser","displayName":"受邀用户","password":"strongpassword123"}
                                """))
                .andExpect(status().isCreated());

        List<Map<String, Object>> rows = queryAuditLogs("INVITE_ACCEPTED");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isNotNull();
    }

    // ---- Invite revoked ----

    @Test
    void inviteRevocationCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();

        String createResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID inviteId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(createResponse, "$.id").toString());

        mockMvc.perform(post("/api/v1/admin/invites/{inviteId}/revoke", inviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = queryAuditLogs("INVITE_REVOKED");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
    }

    // ---- Sensitive data filtering ----

    @Test
    void auditLogDetailsDoNotContainSensitiveData() throws Exception {
        String adminCookie = loginAsAdmin();

        // 1. Login with password
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk());

        // 2. Create member (generates temporary password)
        String memberResponse = mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"AuditUser","displayName":"审计用户","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String tempPassword = com.jayway.jsonpath.JsonPath.read(memberResponse, "$.temporaryPassword").toString();

        // 3. Create invite (generates token)
        String inviteResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inviteLink = com.jayway.jsonpath.JsonPath.read(inviteResponse, "$.inviteLink").toString();
        String rawToken = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

        // 4. Admin reset password
        UUID memberId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(memberResponse, "$.id").toString());
        String resetResponse = mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String resetTempPassword = com.jayway.jsonpath.JsonPath.read(resetResponse, "$.temporaryPassword").toString();

        // 5. Change own password
        String sessionCookie = loginAs("Owner", "correct horse battery staple");
        mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"correct horse battery staple",
                                 "newPassword":"new correct horse battery staple"}
                                """))
                .andExpect(status().isOk());

        // Now verify ALL audit log details don't contain sensitive data
        List<String> allDetails = jdbc.queryForList(
                "SELECT details::text FROM audit_log", String.class);

        for (String detail : allDetails) {
            // No password values
            assertThat(detail).doesNotContain("correct horse battery staple");
            assertThat(detail).doesNotContain("new correct horse battery staple");
            assertThat(detail).doesNotContain("strongpassword123");

            // No temporary passwords
            assertThat(detail).doesNotContain(tempPassword);
            assertThat(detail).doesNotContain(resetTempPassword);

            // No raw invite tokens
            assertThat(detail).doesNotContain(rawToken);

            // No cookie values
            assertThat(detail).doesNotContain("STOCKET_SESSION");

            // No common secret patterns in keys/values
            assertThat(detail.toLowerCase()).doesNotContain("password");
            assertThat(detail.toLowerCase()).doesNotContain("token");
            assertThat(detail.toLowerCase()).doesNotContain("secret");
        }
    }

    // ---- JSONB round-trip ----

    @Test
    void jsonbDetailsRoundTripCorrectly() throws Exception {
        initializeHousehold();

        // Login triggers an event with details
        loginAs("Owner", "correct horse battery staple");

        // Read details from DB as JSONB text
        String detailsJson = jdbc.queryForObject(
                "SELECT details::text FROM audit_log WHERE event_type = 'LOGIN' AND outcome = 'SUCCESS' ORDER BY occurred_at DESC LIMIT 1",
                String.class);

        // Should be valid JSON and parseable
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).isNotNull();
    }

    // ---- All event types verified ----

    @Test
    void allEventTypesAreAudited() throws Exception {
        // Initialize
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated());

        // 1. LOGIN SUCCESS (from initialization)
        assertThat(queryAuditLogs("LOGIN").stream()
                .anyMatch(r -> "SUCCESS".equals(r.get("outcome")))).isTrue();

        // 2. LOGIN FAILURE
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
        assertThat(queryAuditLogs("LOGIN").stream()
                .anyMatch(r -> "FAILURE".equals(r.get("outcome")))).isTrue();

        // Login for subsequent operations
        String adminCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // 3. LOGOUT
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());
        assertThat(queryAuditLogs("LOGOUT")).isNotEmpty();

        // Re-login
        adminCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // 4. MEMBER_CREATED
        UUID memberId = createMemberViaApi(adminCookie, "Member1", "成员1", "MEMBER");
        assertThat(queryAuditLogs("MEMBER_CREATED")).isNotEmpty();

        // 5. MEMBER_ROLE_CHANGED
        mockMvc.perform(patch("/api/v1/admin/members/{memberId}/role", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isOk());
        assertThat(queryAuditLogs("MEMBER_ROLE_CHANGED")).isNotEmpty();

        // 6. MEMBER_DISABLED (need a second admin)
        createMemberViaApi(adminCookie, "Admin2", "管理员2", "ADMIN");
        UUID memberId2 = createMemberViaApi(adminCookie, "DisableTarget", "禁用目标", "MEMBER");
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", memberId2)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());
        assertThat(queryAuditLogs("MEMBER_DISABLED")).isNotEmpty();

        // 7. PASSWORD_RESET_BY_ADMIN
        UUID memberId3 = createMemberViaApi(adminCookie, "ResetTarget", "重置目标", "MEMBER");
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId3)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());
        assertThat(queryAuditLogs("PASSWORD_RESET_BY_ADMIN")).isNotEmpty();

        // 8. PASSWORD_CHANGED
        String freshCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();
        adminCookie = mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", freshCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"correct horse battery staple",
                                 "newPassword":"new correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();
        assertThat(queryAuditLogs("PASSWORD_CHANGED")).isNotEmpty();

        // 9. INVITE_CREATED
        String inviteResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(queryAuditLogs("INVITE_CREATED")).isNotEmpty();

        // 10. INVITE_ACCEPTED
        String inviteLink = com.jayway.jsonpath.JsonPath.read(inviteResponse, "$.inviteLink").toString();
        String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"InvitedUser","displayName":"受邀","password":"strongpassword123"}
                                """))
                .andExpect(status().isCreated());
        assertThat(queryAuditLogs("INVITE_ACCEPTED")).isNotEmpty();

        // 11. INVITE_REVOKED
        String inviteResponse2 = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID revokeInviteId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(inviteResponse2, "$.id").toString());
        mockMvc.perform(post("/api/v1/admin/invites/{inviteId}/revoke", revokeInviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());
        assertThat(queryAuditLogs("INVITE_REVOKED")).isNotEmpty();

        // Verify distinct event types
        List<String> eventTypes = jdbc.queryForList(
                "SELECT DISTINCT event_type FROM audit_log", String.class);
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "LOGIN", "LOGOUT",
                "MEMBER_CREATED", "MEMBER_ROLE_CHANGED", "MEMBER_DISABLED",
                "PASSWORD_RESET_BY_ADMIN", "PASSWORD_CHANGED",
                "INVITE_CREATED", "INVITE_ACCEPTED", "INVITE_REVOKED");
    }

    // ---- Helpers ----

    private void initializeHousehold() throws Exception {
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated());

        // Revoke session created during setup to avoid interference
        jdbc.execute("UPDATE user_session SET revoked_at = now() WHERE revoked_at IS NULL");
        // Clear audit logs from setup
        jdbc.execute("TRUNCATE audit_log");
    }

    private String loginAsAdmin() throws Exception {
        // Ensure household exists
        Integer householdCount = jdbc.queryForObject("SELECT COUNT(*) FROM household", Integer.class);
        if (householdCount == 0) {
            initializeHousehold();
        }

        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("STOCKET_SESSION")
                .getValue();
    }

    private String loginAs(String username, String rawPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, rawPassword)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("STOCKET_SESSION")
                .getValue();
    }

    private UUID createMemberViaApi(String adminCookie, String username,
                                     String displayName, String role) throws Exception {
        String responseJson = mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"%s","role":"%s"}
                                """.formatted(username, displayName, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(responseJson, "$.id").toString());
    }

    private List<Map<String, Object>> queryAuditLogs(String eventType) {
        return jdbc.queryForList(
                "SELECT id, occurred_at, event_type, outcome, actor_account_id, " +
                "subject_type, subject_id, request_id, source, details::text AS details_text " +
                "FROM audit_log WHERE event_type = ?",
                eventType);
    }

    /**
     * Extracts the details field as a JSON text string from a query result row.
     * Handles both direct text and PGobject representations.
     */
    private String getDetailsAsText(Map<String, Object> row) {
        Object details = row.get("details_text");
        if (details instanceof String s) {
            return s;
        }
        // Fallback: try to get the value from a PGobject-like structure
        if (details instanceof org.postgresql.util.PGobject pgo) {
            return pgo.getValue();
        }
        return details.toString();
    }

    private Map<String, Object> parseJson(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Minimal mutable clock for testing time-dependent behavior.
     */
    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
