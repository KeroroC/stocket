package com.stocket.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import com.stocket.identity.internal.member.MemberAdminService;

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
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class MemberAdminIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    /**
     * Provides a MutableClock bean so integration tests can advance time
     * for rate limiter window recovery testing.
     */
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

    @Autowired
    private Clock clock;

    @Autowired
    private MemberAdminService memberAdminService;

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
        // Clear rate limiter state between tests
        memberAdminService.getResetPasswordRateLimiter().clear();
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

    // ---- POST /admin/members/{memberId}/enable ----

    @Test
    void adminCanEnableDisabledMember() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID memberId = createMemberViaApi(adminCookie, "EnableUser", "启用用户", "MEMBER");

        // First disable the member
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        // Verify account is disabled
        String disableStatus = jdbc.queryForObject(
                "SELECT status FROM user_account WHERE normalized_username = 'enableuser'", String.class);
        assertThat(disableStatus).isEqualTo("DISABLED");

        // Now enable the member
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/enable", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // Verify account is enabled
        String enableStatus = jdbc.queryForObject(
                "SELECT status FROM user_account WHERE normalized_username = 'enableuser'", String.class);
        assertThat(enableStatus).isEqualTo("ACTIVE");
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
        String disableStatus = jdbc.queryForObject(
                "SELECT status FROM user_account WHERE normalized_username = 'disableuser'", String.class);
        assertThat(disableStatus).isEqualTo("DISABLED");

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

    @Test
    void resetPasswordRateLimitRecoversAfterWindowExpires() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID memberId = createMemberViaApi(adminCookie, "RecoverTarget", "恢复目标", "MEMBER");

        // Exhaust the rate limit (5 resets)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId)
                            .with(csrf())
                            .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                    .andExpect(status().isOk());
        }

        // 6th should be rate limited
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isTooManyRequests());

        // Advance clock past the 15-minute window
        ((MutableClock) clock).advance(Duration.ofMinutes(16));

        // Should succeed again after window expires
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());
    }

    @Test
    void resetPasswordRateLimiterKeyCountStaysBounded() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");

        // Create 5 different targets and reset each once
        for (int i = 0; i < 5; i++) {
            UUID memberId = createMemberViaApi(adminCookie, "BoundTarget" + i, "目标" + i, "MEMBER");
            mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", memberId)
                            .with(csrf())
                            .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                    .andExpect(status().isOk());
        }

        // Verify key count matches the number of unique targets
        assertThat(memberAdminService.getResetPasswordRateLimiter().trackedKeyCount()).isEqualTo(5);

        // Create one more and reset - key count should not exceed the configured max
        UUID extraMember = createMemberViaApi(adminCookie, "ExtraTarget", "额外目标", "MEMBER");
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", extraMember)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());

        assertThat(memberAdminService.getResetPasswordRateLimiter().trackedKeyCount()).isLessThanOrEqualTo(10000);
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
    void concurrentDemotionOfTwoAdminsPreservesAtLeastOne() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");

        // Create a second admin
        UUID admin2MemberId = createMemberViaApi(adminCookie, "ConcurrentAdmin2", "并发管理员2", "ADMIN");

        UUID admin1AccountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = 'owner'", UUID.class);
        UUID admin1MemberId = jdbc.queryForObject(
                "SELECT id FROM household_member WHERE account_id = ?", UUID.class, admin1AccountId);
        UUID householdId = jdbc.queryForObject(
                "SELECT id FROM household LIMIT 1", UUID.class);

        // Verify we start with 2 admins
        int adminCountBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM household_member m JOIN user_account a ON m.account_id = a.id " +
                "WHERE m.role = 'ADMIN' AND a.status = 'ACTIVE'", Integer.class);
        assertThat(adminCountBefore).isEqualTo(2);

        // Concurrently demote both admins using the service directly
        // (follows SetupIntegrationTest pattern with CountDownLatch + virtual threads)
        int threadCount = 2;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> outcomes = new CopyOnWriteArrayList<>();

        UUID finalAdmin1MemberId = admin1MemberId;
        UUID finalAdmin2MemberId = admin2MemberId;
        UUID finalHouseholdId = householdId;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> f1 = executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    memberAdminService.updateRole(finalHouseholdId, finalAdmin1MemberId, IdentityRole.MEMBER, Instant.now());
                    outcomes.add("SUCCESS");
                } catch (MemberAdminService.LastAdminRequiredException e) {
                    outcomes.add("LAST_ADMIN_REQUIRED");
                } catch (Exception e) {
                    outcomes.add("ERROR:" + e.getMessage());
                }
            });

            Future<?> f2 = executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    memberAdminService.updateRole(finalHouseholdId, finalAdmin2MemberId, IdentityRole.MEMBER, Instant.now());
                    outcomes.add("SUCCESS");
                } catch (MemberAdminService.LastAdminRequiredException e) {
                    outcomes.add("LAST_ADMIN_REQUIRED");
                } catch (Exception e) {
                    outcomes.add("ERROR:" + e.getMessage());
                }
            });

            // Wait for both threads to be ready, then release them simultaneously
            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        }

        // Exactly one should succeed and one should fail
        assertThat(outcomes).hasSize(2);
        assertThat(outcomes).contains("SUCCESS");
        assertThat(outcomes).contains("LAST_ADMIN_REQUIRED");

        // At least one admin must remain
        int adminCountAfter = jdbc.queryForObject(
                "SELECT COUNT(*) FROM household_member m JOIN user_account a ON m.account_id = a.id " +
                "WHERE m.role = 'ADMIN' AND a.status = 'ACTIVE'", Integer.class);
        assertThat(adminCountAfter).isGreaterThanOrEqualTo(1);
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

    // ---- Cross-household access control ----

    @Test
    void adminCannotAccessMemberFromDifferentHousehold() throws Exception {
        String adminCookie = loginAsAdmin("correct horse battery staple");
        UUID memberId = createMemberViaApi(adminCookie, "OwnMember", "本户成员", "MEMBER");

        // Create a fake UUID that doesn't belong to this household
        UUID foreignMemberId = UUID.randomUUID();

        // GET foreign member should return 404
        mockMvc.perform(get("/api/v1/admin/members/{memberId}", foreignMemberId)
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNotFound());

        // Update role of foreign member should return 404
        mockMvc.perform(patch("/api/v1/admin/members/{memberId}/role", foreignMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER"}
                                """))
                .andExpect(status().isNotFound());

        // Disable foreign member should return 404
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/disable", foreignMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNotFound());

        // Reset password of foreign member should return 404
        mockMvc.perform(post("/api/v1/admin/members/{memberId}/reset-password", foreignMemberId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNotFound());

        // Verify own member is still accessible
        mockMvc.perform(get("/api/v1/admin/members/{memberId}", memberId)
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("OwnMember"));
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

    /**
     * Minimal mutable clock for testing time-dependent behavior.
     */
    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
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
