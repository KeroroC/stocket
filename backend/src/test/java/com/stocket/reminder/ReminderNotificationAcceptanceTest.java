package com.stocket.reminder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import com.stocket.notification.internal.channel.SmtpSender;
import com.stocket.notification.internal.channel.WebhookSender;
import com.stocket.notification.internal.worker.DeliveryWorker;
import com.stocket.notification.internal.worker.SendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Testcontainers
@SpringBootTest(properties = {
        "STOCKET_MASTER_KEY=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "stocket.notification.worker-enabled=false"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReminderNotificationAcceptanceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired SecureValueGenerator values;
    @Autowired TokenHasher hasher;
    @Autowired DeliveryWorker worker;
    @MockitoSpyBean WebhookSender webhookSender;
    @MockitoSpyBean SmtpSender smtpSender;

    JdbcTemplate jdbc;
    UUID householdId;
    UUID itemId;
    UUID locationId;
    String session;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
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
                insert into reminder_rule(id,household_id,scope_type,expiration_offsets,low_stock_threshold)
                values (?,?,'HOUSEHOLD',array[7],2)
                """, UUID.randomUUID(), householdId);
        session = createSession();
        insertChannel("IN_APP", "{}");
        insertChannel("WEBHOOK", "{\"url\":\"https://8.8.8.8/hook\"}");
        insertChannel("SMTP", "{\"host\":\"smtp.example.com\",\"port\":587,\"tlsMode\":\"STARTTLS\",\"fromAddress\":\"stocket@example.com\"}");
    }

    @Test
    void keepsInventoryCommittedAndChannelsIndependentAcrossReminderJourney() throws Exception {
        doReturn(SendResult.fromHttp(500, null), SendResult.delivered()).when(webhookSender).send(any());
        doReturn(SendResult.permanent("SMTP_AUTHENTICATION")).when(smtpSender).send(any());

        UUID entryId = receiveExpiringBatch();
        await(() -> count("reminder", "reminder_type='EXPIRING' and status='OPEN'") == 1);
        await(() -> count("notification_delivery", "1=1") == 3);
        UUID expiringReminder = jdbc.queryForObject(
                "select id from reminder where reminder_type='EXPIRING'", UUID.class);

        assertThat(mockMvc.perform(post("/api/v1/reminders/{id}/acknowledge", expiringReminder)
                .with(csrf()).cookie(cookie(session))).andReturn().getResponse().getStatus()).isEqualTo(200);
        consume(entryId, "3");

        await(() -> count("reminder", "reminder_type='LOW_STOCK' and status='OPEN'") == 1);
        await(() -> count("notification_delivery", "1=1") == 6);
        assertThat(jdbc.queryForObject(
                "select available_quantity from inventory_entry where id=?", java.math.BigDecimal.class, entryId))
                .isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject(
                "select status from reminder where id=?", String.class, expiringReminder))
                .isEqualTo("ACKNOWLEDGED");

        assertThat(worker.runBatch(6)).isEqualTo(6);
        assertThat(count("notification_delivery", "channel_type='IN_APP' and status='DELIVERED'"))
                .isEqualTo(2);
        assertThat(count("notification_delivery", "channel_type='SMTP' and status='DEAD'"))
                .isEqualTo(2);
        assertThat(count("notification_delivery", "channel_type='WEBHOOK' and status='RETRY_WAIT'"))
                .isOne();
        assertThat(count("notification_delivery", "channel_type='WEBHOOK' and status='DELIVERED'"))
                .isOne();

        jdbc.update("""
                update notification_delivery set next_attempt_at=now()
                where channel_type='WEBHOOK' and status='RETRY_WAIT'
                """);
        assertThat(worker.runOnce()).isTrue();
        assertThat(count("notification_delivery", "channel_type='WEBHOOK' and status='DELIVERED'"))
                .isEqualTo(2);
        assertThat(count("reminder", "status in ('OPEN','ACKNOWLEDGED','SCHEDULED')"))
                .isEqualTo(2);
        await(() -> count("event_publication", "completion_date is null") == 0);
    }

    private UUID receiveExpiringBatch() throws Exception {
        MvcResult result = command("/api/v1/inventory/receipts", "accept-receive", """
                {"itemId":"%s","type":"BATCH","quantity":"5","locationId":"%s",
                 "receivedAt":"%s","expirationDate":"%s","batchNumber":"B-ACCEPT",
                 "customAttributes":{}}
                """.formatted(itemId, locationId, Instant.now(), LocalDate.now().plusDays(1)));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private void consume(UUID entryId, String quantity) throws Exception {
        MvcResult result = command("/api/v1/inventory/entries/" + entryId + "/consume",
                "accept-consume", "{\"quantity\":\"" + quantity + "\"}");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private MvcResult command(String path, String key, String body) throws Exception {
        return mockMvc.perform(post(path).with(csrf()).cookie(cookie(session))
                .header("Idempotency-Key", key).contentType(APPLICATION_JSON).content(body)).andReturn();
    }

    private int count(String table, String predicate) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + predicate, Integer.class);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private void insertChannel(String type, String configuration) {
        jdbc.update("""
                insert into notification_channel(id,household_id,type,enabled,configuration_json)
                values (?,?,?,true,?::jsonb)
                """, UUID.randomUUID(), householdId, type, configuration);
    }

    private String createSession() {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,email,password_hash,status,
                    credentials_changed_at,created_at,updated_at)
                values (?,'accept-admin','accept-admin','管理员','admin@example.com',?,'ACTIVE',now(),now(),now())
                """, accountId, passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,'ADMIN',now(),now())
                """, UUID.randomUUID(), householdId, accountId);
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
