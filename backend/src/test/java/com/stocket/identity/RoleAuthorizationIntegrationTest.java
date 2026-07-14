package com.stocket.identity;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the three-role authorization matrix using real database accounts.
 * A test-only helper controller is registered via @TestConfiguration
 * to provide endpoints for permission checks without polluting production code.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class RoleAuthorizationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household_member, user_session, member_invite, user_account, household CASCADE");
    }

    /**
     * Test-only configuration providing a helper controller for role checks.
     * The /api/v1/test/write endpoint requires ADMIN or MEMBER role.
     */
    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        TestAuthorizationController testAuthorizationController() {
            return new TestAuthorizationController();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class TestAuthorizationController {

        @PostMapping("/write")
        @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
        ResponseEntity<String> writeEndpoint() {
            return ResponseEntity.ok("OK");
        }
    }

    // ---- All roles can GET self ----

    @Test
    void adminCanGetOwnAccount() throws Exception {
        String adminCookie = setupAndGetAdminCookie();
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void memberCanGetOwnAccount() throws Exception {
        setupAndGetAdminCookie();
        String memberCookie = createAccountAndGetCookie("MemberUser", "memberuser", "MEMBER", "member-password-here");
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void viewerCanGetOwnAccount() throws Exception {
        setupAndGetAdminCookie();
        String viewerCookie = createAccountAndGetCookie("ViewerUser", "vieweruser", "VIEWER", "viewer-password-here");
        mockMvc.perform(get("/api/v1/account")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", viewerCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("VIEWER"));
    }

    // ---- ADMIN and MEMBER can write ----

    @Test
    void adminCanWrite() throws Exception {
        String adminCookie = setupAndGetAdminCookie();
        mockMvc.perform(post("/api/v1/test/write")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());
    }

    @Test
    void memberCanWrite() throws Exception {
        setupAndGetAdminCookie();
        String memberCookie = createAccountAndGetCookie("MemberWriter", "memberwriter", "MEMBER", "member-password-here");
        mockMvc.perform(post("/api/v1/test/write")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie)))
                .andExpect(status().isOk());
    }

    // ---- VIEWER rejected from write ----

    @Test
    void viewerCannotWrite() throws Exception {
        setupAndGetAdminCookie();
        String viewerCookie = createAccountAndGetCookie("ViewerWriter", "viewerwriter", "VIEWER", "viewer-password-here");
        mockMvc.perform(post("/api/v1/test/write")
                        .with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", viewerCookie)))
                .andExpect(status().isForbidden());
    }

    // ---- Only ADMIN can access /admin/** ----

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        String adminCookie = setupAndGetAdminCookie();
        // /api/v1/admin/members list - should succeed for ADMIN
        mockMvc.perform(get("/api/v1/admin/members")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", adminCookie)))
                .andExpect(status().isOk());
    }

    @Test
    void memberCannotAccessAdminEndpoint() throws Exception {
        setupAndGetAdminCookie();
        String memberCookie = createAccountAndGetCookie("MemberAdmin", "memberadmin", "MEMBER", "member-password-here");
        mockMvc.perform(get("/api/v1/admin/members")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", memberCookie)))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotAccessAdminEndpoint() throws Exception {
        setupAndGetAdminCookie();
        String viewerCookie = createAccountAndGetCookie("ViewerAdmin", "vieweradmin", "VIEWER", "viewer-password-here");
        mockMvc.perform(get("/api/v1/admin/members")
                        .cookie(new jakarta.servlet.http.Cookie("STOCKET_SESSION", viewerCookie)))
                .andExpect(status().isForbidden());
    }

    // ---- Helpers ----

    private String setupAndGetAdminCookie() throws Exception {
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

    private String createAccountAndGetCookie(String username, String normalizedUsername,
                                              String role, String rawPassword) throws Exception {
        UUID accountId = UUID.randomUUID();
        String passwordHash = passwordEncoder.encode(rawPassword);
        UUID householdId = jdbc.queryForObject("SELECT id FROM household LIMIT 1", UUID.class);

        // Create account with must_change_password=false for pure role testing
        jdbc.update("""
                INSERT INTO user_account (id, username, normalized_username, display_name,
                password_hash, status, must_change_password, credentials_changed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', false, now(), now(), now(), 0)
                """, accountId, username, normalizedUsername, username, passwordHash);

        jdbc.update("""
                INSERT INTO household_member (id, household_id, account_id, role, created_at, updated_at)
                VALUES (?, ?, ?, ?::varchar, now(), now())
                """, UUID.randomUUID(), householdId, accountId, role);

        // Login to get session cookie
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, rawPassword)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("STOCKET_SESSION"))
                .andReturn()
                .getResponse()
                .getCookie("STOCKET_SESSION")
                .getValue();
    }
}
