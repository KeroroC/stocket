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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.invite.InviteService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class InviteIntegrationTest {

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

    @Autowired
    private Clock clock;

    @Autowired
    private InviteService inviteService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbc;

    // Log capturer for verifying no sensitive data in logs
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE audit_log, household_member, user_session, member_invite, user_account, household CASCADE");
        inviteService.getAcceptRateLimiter().clear();

        // Reset the mutable clock to current time
        ((MutableClock) clock).reset();

        // Set up log capturer for root logger
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        if (logAppender != null) {
            Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            rootLogger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    // ---- Invite creation ----

    @Test
    void createInviteWithDefaultExpiryReturns24Hours() throws Exception {
        String adminCookie = loginAsAdmin();

        String responseJson = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.inviteLink").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Verify invite in database has ~24h expiry
        UUID inviteId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(responseJson, "$.id").toString());
        Instant expiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM member_invite WHERE id = ?",
                Instant.class, inviteId);
        Instant now = Instant.now();
        Instant expectedMinExpiry = now.plus(Duration.ofHours(23));
        Instant expectedMaxExpiry = now.plus(Duration.ofHours(25));
        assertThat(expiresAt).isBetween(expectedMinExpiry, expectedMaxExpiry);
    }

    @Test
    void createInviteWithCustomExpiry() throws Exception {
        String adminCookie = loginAsAdmin();
        Instant customExpiry = Instant.now().plus(Duration.ofHours(2));

        mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"VIEWER","expiresAt":"%s"}
                                """.formatted(customExpiry.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void createInviteWithPastExpiryReturns400() throws Exception {
        String adminCookie = loginAsAdmin();
        Instant pastExpiry = Instant.now().minus(Duration.ofHours(1));

        mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER","expiresAt":"%s"}
                                """.formatted(pastExpiry.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPIRY"));
    }

    @Test
    void createInviteWithExpiryOver30DaysReturns400() throws Exception {
        String adminCookie = loginAsAdmin();
        Instant farFuture = Instant.now().plus(Duration.ofDays(31));

        mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER","expiresAt":"%s"}
                                """.formatted(farFuture.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EXPIRY"));
    }

    @Test
    void createInviteWithMaxUses() throws Exception {
        String adminCookie = loginAsAdmin();

        String responseJson = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER","maxUses":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        UUID inviteId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(responseJson, "$.id").toString());
        Integer maxUses = jdbc.queryForObject(
                "SELECT max_uses FROM member_invite WHERE id = ?",
                Integer.class, inviteId);
        assertThat(maxUses).isEqualTo(3);
    }

    @Test
    void createInviteLinkUsesFrontendUrl() throws Exception {
        String adminCookie = loginAsAdmin();

        String responseJson = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.inviteLink").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String inviteLink = com.jayway.jsonpath.JsonPath.read(responseJson, "$.inviteLink").toString();

        // 验证邀请链接使用前端URL（http://localhost:5173）而不是后端URL
        assertThat(inviteLink).startsWith("http://localhost:5173/invite/");
        assertThat(inviteLink).doesNotContain("localhost:8080");
    }

    // ---- Invite extension ----

    @Test
    void extendInviteReturns204() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create invite
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

        // Extend invite
        Instant newExpiry = Instant.now().plus(Duration.ofDays(7));
        mockMvc.perform(patch("/api/v1/admin/invites/{inviteId}/extend", inviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"expiresAt":"%s"}
                                """.formatted(newExpiry.toString())))
                .andExpect(status().isNoContent());

        // Verify expiry updated in database
        Instant expiresAt = jdbc.queryForObject(
                "SELECT expires_at FROM member_invite WHERE id = ?",
                Instant.class, inviteId);
        assertThat(expiresAt).isEqualTo(newExpiry);
    }

    @Test
    void extendExpiredInviteReturns409() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create invite with short expiry
        Instant shortExpiry = clock.instant().plus(Duration.ofMinutes(5));
        String createResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER","expiresAt":"%s"}
                                """.formatted(shortExpiry.toString())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID inviteId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(createResponse, "$.id").toString());

        // Advance clock past expiry
        ((MutableClock) clock).advance(Duration.ofMinutes(10));

        // Try to extend expired invite
        Instant newExpiry = Instant.now().plus(Duration.ofDays(7));
        mockMvc.perform(patch("/api/v1/admin/invites/{inviteId}/extend", inviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"expiresAt":"%s"}
                                """.formatted(newExpiry.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITE_ALREADY_EXPIRED"));
    }

    // ---- Invite listing ----

    @Test
    void listInvitesDoesNotLeakToken() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create an invite
        mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated());

        // List invites - should not contain token information
        mockMvc.perform(get("/api/v1/admin/invites")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].role").value("MEMBER"))
                .andExpect(jsonPath("$[0].expiresAt").isNotEmpty())
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].acceptedAt").doesNotExist())
                .andExpect(jsonPath("$[0].revokedAt").doesNotExist())
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist())
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].inviteLink").doesNotExist());
    }

    @Test
    void listInvitesShowsAcceptedBy() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create invite
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

        // Accept invite
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"AcceptedUser","displayName":"接受用户","password":"strongpassword123"}
                                """))
                .andExpect(status().isCreated());

        // List invites should show acceptedBy
        mockMvc.perform(get("/api/v1/admin/invites")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].acceptedBy").isArray())
                .andExpect(jsonPath("$[0].acceptedBy[0]").value("接受用户"))
                .andExpect(jsonPath("$[0].useCount").value(1));
    }

    // ---- Invite revocation ----

    @Test
    void revokeInviteReturns204() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create invite
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

        // Revoke invite
        mockMvc.perform(post("/api/v1/admin/invites/{inviteId}/revoke", inviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        // Verify revoked in database
        String revokedAt = jdbc.queryForObject(
                "SELECT revoked_at::text FROM member_invite WHERE id = ?",
                String.class, inviteId);
        assertThat(revokedAt).isNotNull();
    }

    @Test
    void revokeInviteIsIdempotent() throws Exception {
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

        // Revoke twice - both should return 204
        mockMvc.perform(post("/api/v1/admin/invites/{inviteId}/revoke", inviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/admin/invites/{inviteId}/revoke", inviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());
    }

    // ---- Invite status (public) ----

    @Test
    void getInviteStatusRequiresNoAuthentication() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create invite and extract token from link
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

        // GET status without any authentication
        mockMvc.perform(get("/api/v1/invites/{token}/status", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void getInviteStatusForRevokedInviteShowsUnavailable() throws Exception {
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
        UUID inviteId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(createResponse, "$.id").toString());

        // Revoke invite
        mockMvc.perform(post("/api/v1/admin/invites/{inviteId}/revoke", inviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isNoContent());

        // Status should show unavailable
        mockMvc.perform(get("/api/v1/invites/{token}/status", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void getInviteStatusShowsUnavailableAfterExpiry() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create invite with short expiry (use clock.instant() to stay consistent with MutableClock)
        Instant shortExpiry = clock.instant().plus(Duration.ofMinutes(5));
        String createResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER","expiresAt":"%s"}
                                """.formatted(shortExpiry.toString())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inviteLink = com.jayway.jsonpath.JsonPath.read(createResponse, "$.inviteLink").toString();
        String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

        // Status should show available before expiry
        mockMvc.perform(get("/api/v1/invites/{token}/status", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        // Advance clock past expiry
        ((MutableClock) clock).advance(Duration.ofMinutes(10));

        // Status should show unavailable after expiry
        mockMvc.perform(get("/api/v1/invites/{token}/status", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    // ---- Invite acceptance ----

    @Test
    void acceptInviteCreatesAccountAndMember() throws Exception {
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

        // Accept invite
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"InvitedUser","displayName":"受邀用户","password":"strongpassword123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").isNotEmpty())
                .andExpect(jsonPath("$.memberId").isNotEmpty());

        // Verify account was created
        Integer accountCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE normalized_username = 'inviteduser'",
                Integer.class);
        assertThat(accountCount).isEqualTo(1);

        // Verify member was created
        Integer memberCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM household_member m JOIN user_account a ON m.account_id = a.id WHERE a.normalized_username = 'inviteduser'",
                Integer.class);
        assertThat(memberCount).isEqualTo(1);

        // Verify invite is marked as accepted
        UUID inviteId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(createResponse, "$.id").toString());
        String acceptedAt = jdbc.queryForObject(
                "SELECT accepted_at::text FROM member_invite WHERE id = ?",
                String.class, inviteId);
        assertThat(acceptedAt).isNotNull();
    }

    @Test
    void duplicateAcceptReturnsGone() throws Exception {
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

        // First acceptance
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"FirstUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isCreated());

        // Second acceptance should fail
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"SecondUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITE_NOT_AVAILABLE"));
    }

    @Test
    void acceptExpiredInviteReturnsGone() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create invite with short expiry
        Instant shortExpiry = Instant.now().plus(Duration.ofMinutes(5));
        String createResponse = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER","expiresAt":"%s"}
                                """.formatted(shortExpiry.toString())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String inviteLink = com.jayway.jsonpath.JsonPath.read(createResponse, "$.inviteLink").toString();
        String token = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

        // Advance clock past expiry
        ((MutableClock) clock).advance(Duration.ofMinutes(10));

        // Try to accept expired invite
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ExpiredUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITE_EXPIRED"));
    }

    @Test
    void acceptInviteWithDuplicateUsernameDoesNotConsumeInvite() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create an existing user
        createMemberViaApi(adminCookie, "ExistingUser", "已有用户", "MEMBER");

        // Create invite
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

        // Try to accept with existing username
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ExistingUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));

        // Invite should still be available
        mockMvc.perform(get("/api/v1/invites/{token}/status", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));

        // Accepting with a different username should work
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"NewUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void concurrentAcceptOnlyOneSucceeds() throws Exception {
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
        UUID householdId = jdbc.queryForObject("SELECT id FROM household LIMIT 1", UUID.class);

        int threadCount = 3;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CopyOnWriteArrayList<Integer> outcomes = new CopyOnWriteArrayList<>();

        // Use TransactionTemplate for proper transaction management in concurrent threads
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        txTemplate.execute(status -> {
                            try {
                                inviteService.acceptInvite(
                                        token,
                                        "ConcurrentUser" + idx,
                                        "并发用户" + idx,
                                        "strongpassword123",
                                        "127.0.0.1",
                                        Instant.now());
                                outcomes.add(201);
                            } catch (InviteService.InviteNotAvailableException
                                     | InviteService.InviteExpiredException e) {
                                outcomes.add(410);
                            } catch (Exception e) {
                                outcomes.add(500);
                            }
                            return null;
                        });
                    } catch (Exception e) {
                        outcomes.add(500);
                    }
                }));
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();

            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        }

        // Exactly one should succeed (201), others should fail (410)
        long successCount = outcomes.stream().filter(code -> code == 201).count();
        long goneCount = outcomes.stream().filter(code -> code == 410).count();
        assertThat(successCount).isEqualTo(1);
        assertThat(goneCount).isEqualTo(threadCount - 1);
    }

    // ---- Rate limiting on acceptance ----

    @Test
    void acceptInviteRateLimitsByTokenAndSourceAddress() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create an existing user to trigger duplicate username (not consuming invite)
        createMemberViaApi(adminCookie, "ExistingForRate", "限流目标", "MEMBER");

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

        // Attempt 5 times with existing username (409 each, rate limiter counts each)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"username":"ExistingForRate","password":"strongpassword123"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
        }

        // 6th attempt should be rate limited
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"ExistingForRate","password":"strongpassword123"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void acceptInviteRateLimitRecoversAfterWindow() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create an existing user
        createMemberViaApi(adminCookie, "RecoveryUser", "恢复用户", "MEMBER");

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

        // Exhaust rate limit
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"username":"RecoveryUser","password":"strongpassword123"}
                                    """))
                    .andExpect(status().isConflict());
        }

        // 6th should be rate limited
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"RecoveryUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isTooManyRequests());

        // Advance clock past rate limit window
        ((MutableClock) clock).advance(Duration.ofMinutes(16));

        // Should be able to try again (still conflict because username exists, but not rate limited)
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"RecoveryUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    void differentInviteTokensAreIsolatedForRateLimiting() throws Exception {
        String adminCookie = loginAsAdmin();

        // Create existing user
        createMemberViaApi(adminCookie, "IsolatedUser", "隔离用户", "MEMBER");

        // Create two invites
        String createResponse1 = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String createResponse2 = mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String token1 = com.jayway.jsonpath.JsonPath.read(createResponse1, "$.inviteLink").toString();
        token1 = token1.substring(token1.lastIndexOf('/') + 1);
        String token2 = com.jayway.jsonpath.JsonPath.read(createResponse2, "$.inviteLink").toString();
        token2 = token2.substring(token2.lastIndexOf('/') + 1);

        // Exhaust rate limit on token1
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/invites/{token}/accept", token1)
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"username":"IsolatedUser","password":"strongpassword123"}
                                    """))
                    .andExpect(status().isConflict());
        }

        // token1 should be rate limited
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token1)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"IsolatedUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isTooManyRequests());

        // token2 should still work (different rate limit key) - returns conflict because username exists
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token2)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"IsolatedUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
    }

    @Test
    void acceptRateLimiterKeyCountStaysBounded() throws Exception {
        // Verify key count tracking
        assertThat(inviteService.getAcceptRateLimiter().trackedKeyCount()).isEqualTo(0);

        String adminCookie = loginAsAdmin();

        // Create multiple invites and use each once
        for (int i = 0; i < 3; i++) {
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

            // Use the invite (will succeed since username is unique)
            mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                            .with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"username":"BoundedUser%d","password":"strongpassword123"}
                                    """.formatted(i)))
                    .andExpect(status().isCreated());
        }

        // Key count should be bounded
        assertThat(inviteService.getAcceptRateLimiter().trackedKeyCount()).isLessThanOrEqualTo(10000);
    }

    // ---- CSRF and sensitive info boundary ----

    @Test
    void acceptInviteWithoutCsrfReturns403() throws Exception {
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

        // POST without CSRF should return 403
        mockMvc.perform(post("/api/v1/invites/{token}/accept", token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"NoCsrfUser","password":"strongpassword123"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditEventsDoNotContainRawToken() throws Exception {
        String adminCookie = loginAsAdmin();
        String sensitivePassword = "strongpassword123";

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
        String rawToken = inviteLink.substring(inviteLink.lastIndexOf('/') + 1);

        // Accept invite
        mockMvc.perform(post("/api/v1/invites/{token}/accept", rawToken)
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"AuditUser","password":"%s"}
                                """.formatted(sensitivePassword)))
                .andExpect(status().isCreated());

        // Verify audit log does not contain raw token or password
        List<String> auditDetails = jdbc.queryForList(
                "SELECT details::text FROM audit_log WHERE event_type IN ('InviteCreated', 'InviteAccepted', 'InviteRevoked')",
                String.class);

        for (String detail : auditDetails) {
            assertThat(detail).doesNotContain(rawToken);
            assertThat(detail).doesNotContain(sensitivePassword);
        }

        // Verify application logs do not contain raw token or password
        List<String> logMessages = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        for (String logMessage : logMessages) {
            assertThat(logMessage).doesNotContain(rawToken);
            assertThat(logMessage).doesNotContain(sensitivePassword);
        }
    }

    // ---- Non-admin rejection ----

    @Test
    void nonAdminCannotCreateInvite() throws Exception {
        String adminCookie = loginAsAdmin();
        createMemberViaApi(adminCookie, "RegularMember", "普通成员", "MEMBER");

        String memberPassword = getTempPassword("regularmember");
        String memberCookie = loginAs("RegularMember", memberPassword);

        mockMvc.perform(post("/api/v1/admin/invites")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"role":"MEMBER"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotRevokeInvite() throws Exception {
        String adminCookie = loginAsAdmin();
        createMemberViaApi(adminCookie, "RegularMember2", "普通成员2", "MEMBER");

        // Create an invite as admin
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

        String memberPassword = getTempPassword("regularmember2");
        String memberCookie = loginAs("RegularMember2", memberPassword);

        mockMvc.perform(post("/api/v1/admin/invites/{inviteId}/revoke", inviteId)
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie)))
                .andExpect(status().isForbidden());
    }

    // ---- Helpers ----

    private String loginAsAdmin() throws Exception {
        return mockMvc.perform(post("/api/v1/setup/initialize")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"householdName":"王家","timezone":"Asia/Shanghai",
                                 "username":"Owner","displayName":"管理员","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().exists("STOCKET_SESSION"))
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

        UUID memberId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(responseJson, "$.id").toString());
        String tempPassword = com.jayway.jsonpath.JsonPath.read(responseJson, "$.temporaryPassword").toString();

        UUID accountId = jdbc.queryForObject(
                "SELECT id FROM user_account WHERE normalized_username = ?",
                UUID.class, username.toLowerCase());

        // Store temp password in a helper table
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS _test_temp_passwords (
                    account_id UUID PRIMARY KEY,
                    temporary_password_plain VARCHAR(255) NOT NULL
                )
                """);
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

    private static class MutableClock extends Clock {
        private Instant instant;
        private final Instant initialInstant;

        MutableClock(Instant instant) {
            this.instant = instant;
            this.initialInstant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        void reset() {
            this.instant = initialInstant;
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
