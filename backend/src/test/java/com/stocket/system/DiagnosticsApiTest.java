package com.stocket.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DiagnosticsApiTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    static final Path attachmentDir = temp();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("stocket.attachment.directory", () -> attachmentDir.toString()); }
    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;
    JdbcTemplate jdbc;
    UUID householdId;
    UUID accountId;
    String admin;
    String viewer;

    @BeforeEach void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID(); accountId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')", householdId);
        admin = createSession("diag-admin", "ADMIN", accountId);
        viewer = createSession("diag-viewer", "VIEWER", UUID.randomUUID());
        jdbc.update("insert into attachment(id,household_id,owner_type,owner_id,purpose,original_filename,storage_key,detected_media_type,size_bytes,sha256,status,uploaded_by,request_id) values (?,?, 'ITEM_DEFINITION',?,'ITEM_IMAGE','missing.png',?,'image/png',1,repeat('a',64),'MISSING',?,'diagnostic-request')",
                UUID.randomUUID(), householdId, UUID.randomUUID(), "c".repeat(64), accountId);
        jdbc.update("insert into event_publication(id,listener_id,event_type,serialized_event,publication_date) values (?,'test-listener','test-event','{}',now())", UUID.randomUUID());
    }

    @Test void reportsSafeActionableChecksAndRequiresAdmin() throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/diagnostics").cookie(cookie(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.database.status").value("OK"))
                .andExpect(jsonPath("$.checks.attachmentStorage.status").value("OK"))
                .andExpect(jsonPath("$.checks.incompleteEvents.count").value(1))
                .andExpect(jsonPath("$.checks.missingAttachments.count").value(1))
                .andExpect(jsonPath("$.checks.missingAttachments.actionCode").value("REPAIR_ATTACHMENT_STORAGE"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(attachmentDir.toString()).doesNotContain("localhost").doesNotContain("wangpeng");
        mockMvc.perform(get("/api/v1/admin/diagnostics").cookie(cookie(viewer))).andExpect(status().isForbidden());
    }

    private String createSession(String username, String role, UUID id) {
        jdbc.update("insert into user_account(id,username,normalized_username,display_name,password_hash,status,must_change_password,credentials_changed_at,created_at,updated_at,version) values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)", id, username, username, username, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("insert into household_member(id,household_id,account_id,role,created_at,updated_at) values (?,?,?,?,now(),now())", UUID.randomUUID(), householdId, id, role);
        String token = values.generateToken(); Instant now = Instant.now();
        jdbc.update("insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at) values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)", UUID.randomUUID(), id, hasher.sha256(token), now.toString(), now.toString(), now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }
    private jakarta.servlet.http.Cookie cookie(String token) { return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token); }
    private static Path temp() { try { return Files.createTempDirectory("stocket-diagnostics-"); } catch (Exception error) { throw new RuntimeException(error); } }
}
