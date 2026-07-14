package com.stocket.pwa;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PwaWorkflowAcceptanceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;

    JdbcTemplate jdbc;
    UUID householdId;
    UUID categoryId;
    UUID itemId;
    UUID locationId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into category(id,household_id,name,normalized_name,default_inventory_type,attribute_schema)
                values (?,?,'食品','食品','BATCH','[]'::jsonb)
                """, categoryId, householdId);
        jdbc.update("""
                insert into location(id,household_id,name,normalized_name,public_code)
                values (?,?,'冰箱','冰箱','FRIDGE')
                """, locationId, householdId);
    }

    @Test
    void supportsBarcodeLocationReceiveDashboardAndReadOnlyPermissionBoundary() throws Exception {
        String member = createSession("pwa-member", "MEMBER");
        MvcResult created = mockMvc.perform(post("/api/v1/items").with(csrf()).cookie(cookie(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"鲜牛奶","categoryId":"%s","defaultUnit":"盒",
                                 "customAttributes":{},"barcodes":["690001"],"tags":["冷藏"]}
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn();
        itemId = UUID.fromString(JsonPath.read(created.getResponse().getContentAsString(), "$.id"));

        mockMvc.perform(get("/api/v1/catalog/search").cookie(cookie(member)).param("q", "690001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.items[0].matchType").value("BARCODE_EXACT"));
        mockMvc.perform(post("/api/v1/locations/resolve-code").with(csrf()).cookie(cookie(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":\"stocket:location:FRIDGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(locationId.toString()));

        String receipt = """
                {"itemId":"%s","type":"BATCH","quantity":"1.2500","locationId":"%s",
                 "receivedAt":"2026-07-14T00:00:00Z","expirationDate":"2026-08-13",
                 "batchNumber":"PWA-01","customAttributes":{}}
                """.formatted(itemId, locationId);
        mockMvc.perform(post("/api/v1/inventory/receipts").with(csrf()).cookie(cookie(member))
                        .header("Idempotency-Key", "pwa-receive-1")
                        .contentType(MediaType.APPLICATION_JSON).content(receipt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(1.25));
        mockMvc.perform(get("/api/v1/dashboard").cookie(cookie(member)).param("q", "690001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.search[0].id").value(itemId.toString()))
                .andExpect(jsonPath("$.search[0].totalAvailable").value("1.25"))
                .andExpect(jsonPath("$.search[0].locations[0]").value("冰箱"));
        mockMvc.perform(get("/api/v1/reminders").cookie(cookie(member)))
                .andExpect(status().isOk());

        String viewer = createSession("pwa-viewer", "VIEWER");
        mockMvc.perform(post("/api/v1/inventory/receipts").with(csrf()).cookie(cookie(viewer))
                        .header("Idempotency-Key", "pwa-viewer-receive")
                        .contentType(MediaType.APPLICATION_JSON).content(receipt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String createSession(String username, String role) {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)
                """, accountId, username, username, username,
                passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,?,now(),now())
                """, UUID.randomUUID(), householdId, accountId, role);
        String token = values.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,
                    absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }

    private jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }
}
