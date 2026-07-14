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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the identity audit module.
 * Exercises all audit event types and asserts correct persistence
 * in the audit_log table, including sensitive data filtering.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
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

    // ---- HouseholdInitialized ----

    @Test
    void initializationPublishesHouseholdInitializedAndLoginSucceeded() throws Exception {
        mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated());

        List<Map<String, Object>> initEvents = queryAuditLogs("HouseholdInitialized");
        assertThat(initEvents).isNotEmpty();
        assertThat(initEvents.getFirst().get("outcome")).isEqualTo("SUCCESS");
        assertThat(initEvents.getFirst().get("subject_type")).isEqualTo("USER_ACCOUNT");

        List<Map<String, Object>> loginEvents = queryAuditLogs("LoginSucceeded");
        assertThat(loginEvents).isNotEmpty();
    }

    // ---- LoginSucceeded ----

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

        List<Map<String, Object>> rows = queryAuditLogs("LoginSucceeded");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isNotNull();
        assertThat(event.get("subject_type")).isEqualTo("USER_ACCOUNT");
    }

    // ---- LoginFailed ----

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

        List<Map<String, Object>> failureEvents = queryAuditLogs("LoginFailed");
        assertThat(failureEvents).isNotEmpty();

        Map<String, Object> event = failureEvents.getFirst();
        assertThat(event.get("actor_account_id")).isNull();

        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).containsKey("usernameFingerprint");
        assertThat(details.get("usernameFingerprint").toString()).isNotEmpty();
        assertThat(details.get("usernameFingerprint").toString()).isNotEqualTo("owner");
        assertThat(details.get("usernameFingerprint").toString()).isNotEqualTo("Owner");
    }

    // ---- LoggedOut ----

    @Test
    void logoutCreatesAuditEvent() throws Exception {
        initializeHousehold();
        String sessionCookie = loginAs("Owner", "correct horse battery staple");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", sessionCookie)))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = queryAuditLogs("LoggedOut");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isNotNull();
    }

    // ---- SessionRevoked ----

    @Test
    void sessionRevocationCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();

        UUID adminAccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);

        // Create a second session for the admin
        String secondSessionToken = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // Look up the second session ID via the token hash
        String secondSessionHash = new String(org.springframework.security.crypto.codec.Hex.encode(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(secondSessionToken.getBytes())));
        UUID secondSessionId = jdbc.queryForObject(
                "SELECT id FROM user_session WHERE token_hash = ?", UUID.class, secondSessionHash);

        // Revoke the second session
        mockMvc.perform(delete("/api/v1/account/sessions/{sessionId}", secondSessionId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = queryAuditLogs("SessionRevoked");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isEqualTo(adminAccountId);
    }

    // ---- MemberCreated ----

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

        List<Map<String, Object>> rows = queryAuditLogs("MemberCreated");
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

    // ---- MemberRoleChanged ----

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

        List<Map<String, Object>> rows = queryAuditLogs("MemberRoleChanged");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");

        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details.get("oldRole")).isEqualTo("MEMBER");
        assertThat(details.get("newRole")).isEqualTo("VIEWER");
    }

    // ---- MemberStatusChanged ----

    @Test
    void memberDisableCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();
        UUID memberId = createMemberViaApi(adminCookie, "DisableUser", "禁用用户", "MEMBER");

        createMemberViaApi(adminCookie, "SecondAdmin", "第二管理员", "ADMIN");

        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> rows = queryAuditLogs("MemberStatusChanged");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
    }

    // ---- PasswordResetByAdmin ----

    @Test
    void adminPasswordResetCreatesAuditEvent() throws Exception {
        String adminCookie = loginAsAdmin();
        UUID memberId = createMemberViaApi(adminCookie, "ResetUser", "重置用户", "MEMBER");

        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = queryAuditLogs("PasswordResetByAdmin");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isNotNull();

        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).containsKey("targetAccountId");
    }

    // ---- PasswordChanged ----

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

        List<Map<String, Object>> rows = queryAuditLogs("PasswordChanged");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isEqualTo(adminAccountId);
    }

    // ---- InviteCreated ----

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

        List<Map<String, Object>> rows = queryAuditLogs("InviteCreated");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");

        String detailsJson = getDetailsAsText(event);
        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).containsKey("inviteId");
        assertThat(details).containsKey("role");
    }

    // ---- InviteAccepted ----

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

        List<Map<String, Object>> rows = queryAuditLogs("InviteAccepted");
        assertThat(rows).isNotEmpty();

        Map<String, Object> event = rows.getFirst();
        assertThat(event.get("outcome")).isEqualTo("SUCCESS");
        assertThat(event.get("actor_account_id")).isNotNull();
    }

    // ---- InviteRevoked ----

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

        List<Map<String, Object>> rows = queryAuditLogs("InviteRevoked");
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

        loginAs("Owner", "correct horse battery staple");

        String detailsJson = jdbc.queryForObject(
                "SELECT details::text FROM audit_log WHERE event_type = 'LoginSucceeded' ORDER BY occurred_at DESC LIMIT 1",
                String.class);

        Map<String, Object> details = parseJson(detailsJson);
        assertThat(details).isNotNull();
    }

    // ---- All 12 event types verified ----

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

        // 1. HouseholdInitialized
        assertThat(queryAuditLogs("HouseholdInitialized")).isNotEmpty();

        // 2. LoginSucceeded (from initialization)
        assertThat(queryAuditLogs("LoginSucceeded")).isNotEmpty();

        // 3. LoginFailed
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
        assertThat(queryAuditLogs("LoginFailed")).isNotEmpty();

        // Login for subsequent operations
        String adminCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // 4. LoggedOut
        mockMvc.perform(post("/api/v1/auth/logout")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());
        assertThat(queryAuditLogs("LoggedOut")).isNotEmpty();

        // Re-login
        adminCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();

        // 5. MemberCreated
        UUID memberId = createMemberViaApi(adminCookie, "Member1", "成员1", "MEMBER");
        assertThat(queryAuditLogs("MemberCreated")).isNotEmpty();

        // 6. MemberRoleChanged
        mockMvc.perform(patch("/api/v1/admin/members/{memberId}/role", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isOk());
        assertThat(queryAuditLogs("MemberRoleChanged")).isNotEmpty();

        // 7. MemberStatusChanged (need a second admin)
        createMemberViaApi(adminCookie, "Admin2", "管理员2", "ADMIN");
        UUID memberId2 = createMemberViaApi(adminCookie, "DisableTarget", "禁用目标", "MEMBER");
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", memberId2)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());
        assertThat(queryAuditLogs("MemberStatusChanged")).isNotEmpty();

        // 8. PasswordResetByAdmin
        UUID memberId3 = createMemberViaApi(adminCookie, "ResetTarget", "重置目标", "MEMBER");
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId3)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());
        assertThat(queryAuditLogs("PasswordResetByAdmin")).isNotEmpty();

        // 9. SessionRevoked (revoke other sessions)
        // Create an extra session for admin
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk());

        // Revoke other sessions
        mockMvc.perform(delete("/api/v1/account/sessions/others")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());
        assertThat(queryAuditLogs("SessionRevoked")).isNotEmpty();

        // 10. PasswordChanged
        adminCookie = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Owner","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();
        adminCookie = mockMvc.perform(post("/api/v1/account/password")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"oldPassword":"correct horse battery staple",
                                 "newPassword":"new correct horse battery staple"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("STOCKET_SESSION").getValue();
        assertThat(queryAuditLogs("PasswordChanged")).isNotEmpty();

        // 11. InviteCreated
        String inviteResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(queryAuditLogs("InviteCreated")).isNotEmpty();

        // 12. InviteAccepted
        String inviteLink = com.jayway.jsonpath.JsonPath.read(inviteResponse, "$.inviteLink").toString();
        String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"InvitedUser","displayName":"受邀","password":"strongpassword123"}
                                """))
                .andExpect(status().isCreated());
        assertThat(queryAuditLogs("InviteAccepted")).isNotEmpty();

        // 13. InviteRevoked
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
        assertThat(queryAuditLogs("InviteRevoked")).isNotEmpty();

        // Verify distinct event types (PasswordRecoveredLocally belongs to Task 9)
        List<String> eventTypes = jdbc.queryForList(
                "SELECT DISTINCT event_type FROM audit_log", String.class);
        assertThat(eventTypes).containsExactlyInAnyOrder(
                "HouseholdInitialized", "LoginSucceeded", "LoginFailed",
                "LoggedOut", "SessionRevoked",
                "MemberCreated", "MemberRoleChanged", "MemberStatusChanged",
                "PasswordResetByAdmin", "PasswordChanged",
                "InviteCreated", "InviteAccepted", "InviteRevoked");
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

        jdbc.execute("UPDATE user_session SET revoked_at = now() WHERE revoked_at IS NULL");
        jdbc.execute("TRUNCATE audit_log");
    }

    private String loginAsAdmin() throws Exception {
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

    private String getDetailsAsText(Map<String, Object> row) {
        Object details = row.get("details_text");
        if (details instanceof String s) {
            return s;
        }
        if (details instanceof org.postgresql.util.PGobject pgo) {
            return pgo.getValue();
        }
        return details.toString();
    }

    private Map<String, Object> parseJson(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

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
