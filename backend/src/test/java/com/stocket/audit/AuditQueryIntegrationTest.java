package com.stocket.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class AuditQueryIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");
    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;
    JdbcTemplate jdbc;
    UUID householdId;
    UUID actorId;
    String admin;
    String viewer;

    @BeforeEach void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID(); actorId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'家','Asia/Shanghai')", householdId);
        admin = createSession("audit-admin", "ADMIN", actorId);
        viewer = createSession("audit-viewer", "VIEWER", UUID.randomUUID());
        Instant now = Instant.now();
        insert(now.minusSeconds(1), "AttachmentUploaded", "SUCCESS", "request-a", UUID.randomUUID());
        insert(now.minusSeconds(2), "InventoryReceived", "SUCCESS", "request-b", UUID.randomUUID());
        insert(now.minusSeconds(3), "LoginFailed", "FAILURE", "request-c", UUID.randomUUID());
        insert(now.minusSeconds(4), "AttachmentDeleted", "SUCCESS", "request-d", UUID.randomUUID());
    }

    @Test void filtersAndCursorPagesWithoutDuplicatesAndRequiresAdmin() throws Exception {
        MvcResult first = mockMvc.perform(get("/api/v1/admin/audit-logs").param("size", "2").cookie(cookie(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").isString()).andReturn();
        List<String> firstIds = JsonPath.read(first.getResponse().getContentAsString(), "$.items[*].id");
        String cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");
        MvcResult second = mockMvc.perform(get("/api/v1/admin/audit-logs").param("size", "2")
                        .param("cursor", cursor).cookie(cookie(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andReturn();
        List<String> secondIds = JsonPath.read(second.getResponse().getContentAsString(), "$.items[*].id");
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);

        mockMvc.perform(get("/api/v1/admin/audit-logs").param("requestId", "request-c").cookie(cookie(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventType").value("LoginFailed"))
                .andExpect(jsonPath("$.items[0].outcome").value("FAILURE"));
        mockMvc.perform(get("/api/v1/admin/audit-logs").cookie(cookie(viewer))).andExpect(status().isForbidden());
    }

    private void insert(Instant occurredAt, String type, String outcome, String requestId, UUID subjectId) {
        jdbc.update("insert into audit_log(id,household_id,occurred_at,event_type,outcome,actor_account_id,subject_type,subject_id,request_id,source,details) values (?,?,?,?,?,?,'TEST',?,?, 'api','{\"safe\":true}'::jsonb)",
                UUID.randomUUID(), householdId, java.sql.Timestamp.from(occurredAt), type, outcome, actorId, subjectId, requestId);
    }

    private String createSession(String username, String role, UUID accountId) {
        jdbc.update("insert into user_account(id,username,normalized_username,display_name,password_hash,status,must_change_password,credentials_changed_at,created_at,updated_at,version) values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)", accountId, username, username, username, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("insert into household_member(id,household_id,account_id,role,created_at,updated_at) values (?,?,?,?,now(),now())", UUID.randomUUID(), householdId, accountId, role);
        String token = values.generateToken(); Instant now = Instant.now();
        jdbc.update("insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at) values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)", UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(), now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }
    private jakarta.servlet.http.Cookie cookie(String token) { return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token); }
}
