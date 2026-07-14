package com.stocket.inventory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DisabledInAotMode
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ReceiveInventoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;

    private JdbcTemplate jdbc;
    private UUID householdId;
    private UUID itemId;
    private UUID locationId;

    @BeforeEach
    void setUp() throws InterruptedException {
        jdbc = new JdbcTemplate(dataSource);
        awaitEventPublications();
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        insertItem(itemId, null, null, false);
        insertLocation(locationId, false);
    }

    @AfterEach
    void awaitPublishedEventsAfterTest() throws InterruptedException {
        awaitEventPublications();
    }

    private void awaitEventPublications() throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (outstandingEventPublications() > 0 && System.nanoTime() < deadline) Thread.sleep(25);
        assertThat(outstandingEventPublications()).isZero();
    }

    private int outstandingEventPublications() {
        Integer count = jdbc.queryForObject("select count(*) from event_publication where completion_date is null", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    void receivesBatchAndUsesCatalogDefaultShelfLife() throws Exception {
        String member = createSession("member", "MEMBER");
        jdbc.update("update item_definition set default_shelf_life_value=1, default_shelf_life_unit='MONTH' where id=?",
                itemId);

        MvcResult result = receive(member, "receive-batch", batchRequest(itemId, locationId, "2.5000", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BATCH"))
                .andExpect(jsonPath("$.quantity").value("2.5"))
                .andExpect(jsonPath("$.expirationDate").value("2026-02-28"))
                .andReturn();

        UUID entryId = responseId(result);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.requestId"))
                .isEqualTo("inventory-request-123");
        assertThat(jdbc.queryForObject("select available_quantity from inventory_entry where id=?",
                String.class, entryId)).isEqualTo("2.5000");
        assertThat(jdbc.queryForObject("select request_id from inventory_movement where entry_id=?", String.class, entryId))
                .isEqualTo("inventory-request-123");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where event_type='InventoryReceived' and subject_id=? and request_id='inventory-request-123'", Integer.class, entryId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movement where entry_id=? and movement_type='RECEIVE'",
                Integer.class, entryId)).isEqualTo(1);
    }

    @Test
    void receivesAssetAndExplicitExpirationWins() throws Exception {
        String admin = createSession("admin", "ADMIN");

        MvcResult result = receive(admin, "receive-asset", assetRequest(itemId, locationId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ASSET"))
                .andExpect(jsonPath("$.quantity").value("1"))
                .andExpect(jsonPath("$.expirationDate").value("2027-01-01"))
                .andReturn();

        UUID entryId = responseId(result);
        assertThat(jdbc.queryForObject("select asset_number from asset_detail where inventory_entry_id=?",
                String.class, entryId)).isEqualTo("ASSET-001");
    }

    @Test
    void enforcesIdempotencyHeaderReplayAndRequestBinding() throws Exception {
        String member = createSession("member", "MEMBER");
        String request = batchRequest(itemId, locationId, "2", "2027-01-01");

        mockMvc.perform(post("/api/v1/inventory/receipts").with(csrf()).cookie(cookie(member))
                        .contentType(APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        MvcResult first = receive(member, "stable-key", request)
                .andExpect(status().isCreated()).andReturn();
        MvcResult replay = receive(member, "stable-key", request)
                .andExpect(status().isCreated()).andReturn();

        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        receive(member, "stable-key", batchRequest(itemId, locationId, "3", "2027-01-01"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(jdbc.queryForObject("select count(*) from inventory_entry", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movement", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsViewerArchivedItemAndArchivedLocation() throws Exception {
        String viewer = createSession("viewer", "VIEWER");
        receive(viewer, "viewer-key", batchRequest(itemId, locationId, "1", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        String member = createSession("member", "MEMBER");
        jdbc.update("update item_definition set archived_at=now() where id=?", itemId);
        receive(member, "archived-item", batchRequest(itemId, locationId, "1", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_ARCHIVED"));

        jdbc.update("update item_definition set archived_at=null where id=?", itemId);
        jdbc.update("update location set archived_at=now() where id=?", locationId);
        receive(member, "archived-location", batchRequest(itemId, locationId, "1", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_ARCHIVED"));
    }

    private org.springframework.test.web.servlet.ResultActions receive(
            String token, String key, String request) throws Exception {
        return mockMvc.perform(post("/api/v1/inventory/receipts").with(csrf()).cookie(cookie(token))
                .header("Idempotency-Key", key).header("X-Request-Id", "inventory-request-123")
                .contentType(APPLICATION_JSON).content(request));
    }

    private String batchRequest(UUID item, UUID location, String quantity, String expirationDate) {
        String expiration = expirationDate == null ? "null" : "\"" + expirationDate + "\"";
        return """
                {"itemId":"%s","type":"BATCH","quantity":"%s","locationId":"%s",
                 "receivedAt":"2026-07-14T00:00:00Z","productionDate":"2026-01-31",
                 "expirationDate":%s,"batchNumber":"B-001","customAttributes":{"cold":true}}
                """.formatted(item, quantity, location, expiration);
    }

    private String assetRequest(UUID item, UUID location) {
        return """
                {"itemId":"%s","type":"ASSET","quantity":"1","locationId":"%s",
                 "receivedAt":"2026-07-14T00:00:00Z","productionDate":"2026-01-31",
                 "expirationDate":"2027-01-01","shelfLifeValue":1,"shelfLifeUnit":"MONTH",
                 "assetNumber":"ASSET-001","serialNumber":"SN-001","customAttributes":{}}
                """.formatted(item, location);
    }

    private void insertItem(UUID id, Integer shelfLife, String unit, boolean archived) {
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,
                    default_shelf_life_value,default_shelf_life_unit,custom_attributes,archived_at)
                values (?,?,'牛奶','牛奶','盒',?,?, '{}'::jsonb, case when ? then now() else null end)
                """, id, householdId, shelfLife, unit, archived);
    }

    private void insertLocation(UUID id, boolean archived) {
        jdbc.update("""
                insert into location(id,household_id,name,normalized_name,public_code,archived_at)
                values (?,?,'冰箱','冰箱',?,case when ? then now() else null end)
                """, id, householdId, UUID.randomUUID().toString(), archived);
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
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return token;
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }
}
