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
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
class MemberAdminIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");
        // Create test helper table for storing temp passwords
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS _test_temp_passwords (
                    account_id UUID PRIMARY KEY,
                    temporary_password_plain VARCHAR(255) NOT NULL
                )
                """);
        jdbc.execute("TRUNCATE _test_temp_passwords");
    }

    // ---- POST /admin/members ----

    @Test
    void adminCreateMemberReturnsTemporaryPasswordAndSetsMustChangePassword() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");

        mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"NewUser","displayName":"新用户","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.username").value("NewUser"))
                .andExpect(jsonPath("$.displayName").value("新用户"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.temporaryPassword").value(org.hamcrest.Matchers.hasLength(20)));

        // Verify must_change_password is true in database
        Boolean mustChange = jdbc.queryForObject(
                "SELECT must_change_password FROM user_account WHERE normalized_username = 'newuser'",
                Boolean.class);
        assertThat(mustChange).isTrue();
    }

    @Test
    void createdMemberCanLoginWithTemporaryPassword() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");

        String responseJson = mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"LoginUser","displayName":"登录用户","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract temporary password from response
        String tempPassword = com.jayway.jsonpath.JsonPath.read(responseJson, "$.temporaryPassword").toString();

        // Login with temporary password should work
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"LoginUser","password":"%s"}
                                """.formatted(tempPassword)))
                .andExpect(status().isOk());
    }

    @Test
    void temporaryPasswordIsOnlyReturnedOnceInCreateResponse() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");

        // Create member
        String responseJson = mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"OnceUser","displayName":"一次性用户","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String tempPassword = com.jayway.jsonpath.JsonPath.read(responseJson, "$.temporaryPassword").toString();
        assertThat(tempPassword).isNotNull().isNotEmpty();

        // GET member should NOT return temporary password
        UUID memberId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(responseJson, "$.id").toString());

        mockMvc.perform(get("/api/v1/admin/members/{memberId}", memberId)
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }

    // ---- PATCH /admin/members/{memberId}/role ----

    @Test
    void adminCanUpdateMemberRole() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID memberId = createMemberViaApi(adminCookie, "RoleUser", "角色用户", "MEMBER");

        mockMvc.perform(patch("/api/v1/admin/members/{memberId}/role", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));

        // Verify in database
        String role = jdbc.queryForObject(
                "SELECT m.role FROM household_member m JOIN user_account a ON m.account_id = a.id WHERE a.normalized_username = 'roleuser'",
                String.class);
        assertThat(role).isEqualTo("VIEWER");
    }

    // ---- POST /admin/members/{memberId}/disable ----

    @Test
    void adminCanDisableMemberAndPreventLogin() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID memberId = createMemberViaApi(adminCookie, "DisableUser", "禁用用户", "MEMBER");

        String memberPassword = getTempPassword("disableuser");

        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        // Verify account is disabled
        String status = jdbc.queryForObject(
                "SELECT status FROM user_account WHERE normalized_username = 'disableuser'", String.class);
        assertThat(status).isEqualTo("DISABLED");

        // Login should fail
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"DisableUser","password":"%s"}
                                """.formatted(memberPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ---- POST /admin/members/{memberId}/reset-password ----

    @Test
    void adminResetPasswordReturnsNewTemporaryPasswordAndRevokesOldSessions() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID memberId = createMemberViaApi(adminCookie, "ResetUser", "重置用户", "MEMBER");

        // Login with the temporary password to create a session
        String tempPassword = getTempPassword("resetuser");

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ResetUser","password":"%s"}
                                """.formatted(tempPassword)))
                .andExpect(status().isOk());

        UUID accountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'resetuser'", UUID.class);

        // Verify session exists
        Integer sessionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, accountId);
        assertThat(sessionsBefore).isEqualTo(1);

        // Reset password
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.temporaryPassword").value(org.hamcrest.Matchers.hasLength(20)));

        // Old sessions should be revoked
        Integer activeSessionsAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_session WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class, accountId);
        assertThat(activeSessionsAfter).isEqualTo(0);

        // must_change_password should be true again
        Boolean mustChange = jdbc.queryForObject(
                "SELECT must_change_password FROM user_account WHERE id = ?", Boolean.class, accountId);
        assertThat(mustChange).isTrue();
    }

    @Test
    void resetPasswordRateLimitsByActorAndTargetPair() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID memberId1 = createMemberViaApi(adminCookie, "Target1", "目标1", "MEMBER");
        UUID memberId2 = createMemberViaApi(adminCookie, "Target2", "目标2", "MEMBER");

        // Reset target1 5 times (within limit)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId1)
                            .with(csrf())
                            .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                    .andExpect(status().isOk());
        }

        // 6th reset for target1 should be rate limited
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId1)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // Reset for target2 should still work (different key)
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId2)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());
    }

    // ---- Last admin protection ----

    @Test
    void disablingLastAdminReturns409() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID adminAccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        UUID adminMemberId = jdbc.queryForObject(
                "SELECT id FROM household_member WHERE account_id = ?", UUID.class, adminAccountId);

        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", adminMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
    }

    @Test
    void demotingLastAdminReturns409() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID adminAccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        UUID adminMemberId = jdbc.queryForObject(
                "SELECT id FROM household_member WHERE account_id = ?", UUID.class, adminAccountId);

        mockMvc.perform(patch("/api/v1/admin/members/{memberId}/role", adminMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
    }

    @Test
    void disablingAdminAllowedWhenSecondAdminExists() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");

        // Create a second admin
        createMemberViaApi(adminCookie, "SecondAdmin", "第二管理员", "ADMIN");

        UUID firstAdminAccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        UUID firstAdminMemberId = jdbc.queryForObject(
                "SELECT id FROM household_member WHERE account_id = ?", UUID.class, firstAdminAccountId);

        // Now disabling the first admin should succeed
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", firstAdminMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());
    }

    @Test
    void demotingAdminAllowedWhenSecondAdminExists() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");

        // Create a second admin
        createMemberViaApi(adminCookie, "SecondAdmin2", "第二管理员2", "ADMIN");

        UUID firstAdminAccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        UUID firstAdminMemberId = jdbc.queryForObject(
                "SELECT id FROM household_member WHERE account_id = ?", UUID.class, firstAdminAccountId);

        // Demoting first admin should succeed
        mockMvc.perform(patch("/api/v1/admin/members/{memberId}/role", firstAdminMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    // ---- Non-admin rejection ----

    @Test
    void memberCannotCreateMembers() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        createMemberViaApi(adminCookie, "RegularMember", "普通成员", "MEMBER");

        // Login as member
        String memberPassword = getTempPassword("regularmember");
        String memberCookie = loginAs("RegularMember", memberPassword);

        mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Blocked","displayName":"被拒绝","role":"MEMBER"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotCreateMembers() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        createMemberViaApi(adminCookie, "RegularViewer", "普通查看者", "VIEWER");

        // Login as viewer
        String viewerPassword = getTempPassword("regularviewer");
        String viewerCookie = loginAs("RegularViewer", viewerPassword);

        mockMvc.perform(post("/api/v1/admin/members")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", viewerCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"Blocked2","displayName":"被拒绝2","role":"MEMBER"}
                                """))
                .andExpect(status().isForbidden());
    }

    // ---- Helpers ----

    private String loginAsAdmin(String rawPassword) throws Exception {
        return mockMvc.perform(post("/api/v1/setup/initialize")
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

    /**
     * Creates a member via admin API and stores the temporary password in a test table.
     * Returns the member ID.
     */
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

        UUID memberId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(responseJson, "$.id").toString());
        String tempPassword = com.jayway.jsonpath.JsonPath.read(responseJson, "$.temporaryPassword").toString();

        // Store temp password for test retrieval
        UUID accountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = ?",
                UUID.class, username.toLowerCase());

        jdbc.update("""
                INSERT INTO _test_temp_passwords (account_id, temporary_password_plain)
                VALUES (?, ?) ON CONFLICT (account_id) DO UPDATE SET temporary_password_plain = ?
                """, accountId, tempPassword, tempPassword);

        return memberId;
    }

    private String getTempPassword(String normalizedUsername) {
        return jdbc.queryForObject(
                "SELECT t.temporary_password_plain FROM _test_temp_passwords t " +
                "JOIN user_account a ON t.account_id = a.id WHERE a.normalized_username = ?",
                String.class, normalizedUsername);
    }
}
