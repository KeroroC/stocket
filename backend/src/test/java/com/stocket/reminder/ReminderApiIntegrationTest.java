package com.stocket.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import com.stocket.reminder.internal.lifecycle.ReminderDueJob;
import com.stocket.reminder.internal.lifecycle.ReminderRecalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReminderApiIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;
    @Autowired ReminderRecalculator recalculator;
    @Autowired ReminderDueJob dueJob;

    JdbcTemplate jdbc;
    UUID householdId;
    UUID categoryId;
    UUID itemId;
    UUID locationId;
    UUID entryId;
    String adminSession;
    String viewerSession;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        entryId = UUID.randomUUID();

        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into category(id,household_id,name,normalized_name,default_inventory_type,attribute_schema)
                values (?,?,'食品','食品','BATCH','[]'::jsonb)
                """, categoryId, householdId);
        jdbc.update("""
                insert into item_definition(id,household_id,category_id,name,normalized_name,default_unit,
                    custom_attributes)
                values (?,?,?,'牛奶','牛奶','盒','{}'::jsonb)
                """, itemId, householdId, categoryId);
        jdbc.update("""
                insert into location(id,household_id,name,normalized_name,public_code)
                values (?,?,'冰箱','冰箱',?)
                """, locationId, householdId, UUID.randomUUID().toString());
        jdbc.update("""
                insert into inventory_entry(id,household_id,item_definition_id,location_id,inventory_type,
                    available_quantity,received_at,expiration_date,custom_attributes)
                values (?,?,?,?,'BATCH',1,now(),?,'{}'::jsonb)
                """, entryId, householdId, itemId, locationId, LocalDate.now().plusDays(10));
        jdbc.update("""
                insert into reminder_rule(id,household_id,scope_type,expiration_offsets,low_stock_threshold)
                values (?,?,'HOUSEHOLD',array[7,1,0],5)
                """, UUID.randomUUID(), householdId);
        adminSession = createSession("reminder-admin", "ADMIN");
        viewerSession = createSession("reminder-viewer", "VIEWER");
    }

    @AfterEach
    void awaitNotificationPlanning() throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (outstandingPublications() > 0 && System.nanoTime() < deadline) Thread.sleep(25);
        assertThat(outstandingPublications()).isZero();
    }

    private int outstandingPublications() {
        Integer count = jdbc.queryForObject("select count(*) from event_publication where completion_date is null", Integer.class);
        return count == null ? 0 : count;
    }

    @Test
    void supportsFilteringStablePagingAcknowledgementRulesAndDueOpening() throws Exception {
        Instant sameTrigger = Instant.now().minus(2, ChronoUnit.HOURS);
        UUID firstOpen = insertReminder("EXPIRING", "manual:1", sameTrigger, "OPEN", entryId);
        UUID secondOpen = insertReminder("EXPIRING", "manual:2", sameTrigger, "OPEN", entryId);
        UUID lowStock = insertReminder("LOW_STOCK", "LOW_STOCK:5", sameTrigger, "OPEN", null);

        MvcResult pageZero = mockMvc.perform(get("/api/v1/reminders")
                        .cookie(cookie(viewerSession))
                        .param("status", "OPEN").param("type", "EXPIRING")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.content[0].itemName").value("牛奶"))
                .andExpect(jsonPath("$.content[0].locationName").value("冰箱"))
                .andReturn();
        MvcResult pageOne = mockMvc.perform(get("/api/v1/reminders")
                        .cookie(cookie(viewerSession))
                        .param("status", "OPEN").param("type", "EXPIRING")
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andReturn();
        assertThat(JsonPath.<String>read(pageZero.getResponse().getContentAsString(), "$.content[0].id"))
                .isNotEqualTo(JsonPath.read(pageOne.getResponse().getContentAsString(), "$.content[0].id"));

        mockMvc.perform(post("/api/v1/reminders/{id}/acknowledge", lowStock)
                        .with(csrf()).cookie(cookie(viewerSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        jdbc.update("update inventory_entry set available_quantity=10 where id=?", entryId);
        recalculator.recalculate(householdId, itemId, entryId, "api-request");
        assertThat(jdbc.queryForObject("select status from reminder where id=?", String.class, lowStock))
                .isEqualTo("RESOLVED");

        String householdRule = """
                {"expirationOffsets":[30,7,1,0],"lowStockThreshold":"4","enabled":true,"version":0}
                """;
        mockMvc.perform(put("/api/v1/reminder-rules/HOUSEHOLD/{scopeId}", householdId)
                        .with(csrf()).cookie(cookie(viewerSession))
                        .contentType(APPLICATION_JSON).content(householdRule))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/reminder-rules/HOUSEHOLD/{scopeId}", householdId)
                        .with(csrf()).cookie(cookie(adminSession))
                        .contentType(APPLICATION_JSON).content(householdRule))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(put("/api/v1/reminder-rules/HOUSEHOLD/{scopeId}", householdId)
                        .with(csrf()).cookie(cookie(adminSession))
                        .contentType(APPLICATION_JSON).content(householdRule))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mockMvc.perform(put("/api/v1/reminder-rules/CATEGORY/{scopeId}", categoryId)
                        .with(csrf()).cookie(cookie(adminSession)).contentType(APPLICATION_JSON)
                        .content("""
                                {"expirationOffsets":[14,3,0],"lowStockThreshold":null,
                                 "enabled":true,"version":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/reminder-rules/ITEM/{scopeId}", itemId)
                        .with(csrf()).cookie(cookie(adminSession)).contentType(APPLICATION_JSON)
                        .content("""
                                {"expirationOffsets":[2,0],"lowStockThreshold":"2",
                                 "enabled":true,"version":0}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reminder-rules").cookie(cookie(viewerSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        UUID due = insertReminder("EXPIRING", "manual:due", Instant.now().minusSeconds(5),
                "SCHEDULED", entryId);
        assertThat(dueJob.openDue()).isOne();
        assertThat(jdbc.queryForObject("select status from reminder where id=?", String.class, due))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("select opened_at is not null from reminder where id=?",
                Boolean.class, due)).isTrue();

        assertThat(firstOpen).isNotEqualTo(secondOpen);
    }

    private UUID insertReminder(String type, String key, Instant triggerAt, String status, UUID reminderEntryId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into reminder(id,household_id,item_definition_id,inventory_entry_id,reminder_type,
                    trigger_key,trigger_at,status,opened_at,created_at,updated_at)
                values (?,?,?,?,?,?,?::timestamptz,?,?,now(),now())
                """, id, householdId, itemId, reminderEntryId, type, key, triggerAt.toString(), status,
                "OPEN".equals(status) ? java.sql.Timestamp.from(triggerAt) : null);
        return id;
    }

    private String createSession(String username, String role) {
        UUID accountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)
                """, accountId, username, username, username,
                passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,?,now(),now())
                """, memberId, householdId, accountId, role);
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
