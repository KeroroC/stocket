package com.stocket.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "STOCKET_MASTER_KEY=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "stocket.notification.worker-enabled=false"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationAdminApiTest {

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
    UUID memberId;
    UUID reminderId;
    String adminSession;
    String memberSession;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        adminSession = createSession("notification-admin", "ADMIN").token();
        Session member = createSession("notification-member", "MEMBER");
        memberSession = member.token();
        memberId = member.memberId();
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes)
                values (?,?,'牛奶','牛奶','盒','{}'::jsonb)
                """, itemId, householdId);
        reminderId = UUID.randomUUID();
        jdbc.update("""
                insert into reminder(id,household_id,item_definition_id,reminder_type,trigger_key,
                    trigger_at,status,opened_at,created_at,updated_at)
                values (?,?,?,'LOW_STOCK','LOW_STOCK:2',now(),'OPEN',now(),now(),now())
                """, reminderId, householdId, itemId);
    }

    @Test
    void managesCurrentPushSubscriptionAndAdminDeadDeliveriesWithoutExposingSecrets() throws Exception {
        String subscription = """
                {"endpoint":"https://push.example.com/subscriptions/very-secret-endpoint",
                 "p256dh":"public-key-material","auth":"auth-secret"}
                """;
        mockMvc.perform(put("/api/v1/notification/push-subscription")
                        .with(csrf()).cookie(cookie(memberSession)).contentType(APPLICATION_JSON)
                        .content(subscription))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpointSummary").value(org.hamcrest.Matchers.startsWith("https://push.example.com/")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("very-secret-endpoint"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("auth-secret"))));
        assertThat(jdbc.queryForObject("select count(*) from push_subscription where member_id=? and enabled=true",
                Integer.class, memberId)).isOne();
        assertThat(jdbc.queryForObject("select encrypted_endpoint from push_subscription where member_id=?",
                String.class, memberId)).doesNotContain("very-secret-endpoint");

        mockMvc.perform(delete("/api/v1/notification/push-subscription")
                        .with(csrf()).cookie(cookie(memberSession)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from push_subscription where member_id=? and enabled=true",
                Integer.class, memberId)).isZero();

        UUID deliveryId = insertDeadDelivery();
        mockMvc.perform(get("/api/v1/admin/notification/deliveries")
                        .cookie(cookie(memberSession)).param("status", "DEAD"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/notification/deliveries")
                        .cookie(cookie(adminSession)).param("status", "DEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].id").value(deliveryId.toString()))
                .andExpect(jsonPath("$.content[0].lastErrorCode").value("HTTP_400"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("very-secret-endpoint"))));

        Instant historicalErrorAt = jdbc.queryForObject(
                "select last_error_at from notification_delivery where id=?",
                java.time.OffsetDateTime.class, deliveryId).toInstant();
        mockMvc.perform(post("/api/v1/admin/notification/deliveries/{id}/retry", deliveryId)
                        .with(csrf()).cookie(cookie(adminSession)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0));
        assertThat(jdbc.queryForObject("select last_error_code from notification_delivery where id=?",
                String.class, deliveryId)).isNull();
        assertThat(jdbc.queryForObject("select last_error_at from notification_delivery where id=?",
                java.time.OffsetDateTime.class, deliveryId).toInstant()).isEqualTo(historicalErrorAt);
    }

    private UUID insertDeadDelivery() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into notification_delivery(id,household_id,reminder_id,member_id,channel_type,
                    dedupe_key,status,attempt_count,last_error_code,last_error_at,created_at,updated_at)
                values (?,?,?,?, 'IN_APP',?,'DEAD',8,'HTTP_400',now()-interval '1 hour',now(),now())
                """, id, householdId, reminderId, memberId, id + ":dedupe");
        return id;
    }

    private Session createSession(String username, String role) {
        UUID accountId = UUID.randomUUID();
        UUID createdMemberId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,?,?,?,?,'ACTIVE',false,now(),now(),now(),0)
                """, accountId, username, username, username,
                passwordEncoder.encode("correct horse battery staple"));
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,?,now(),now())
                """, createdMemberId, householdId, accountId, role);
        String token = values.generateToken();
        Instant now = Instant.now();
        jdbc.update("""
                insert into user_session(id,account_id,token_hash,created_at,last_seen_at,idle_expires_at,
                    absolute_expires_at)
                values (?,?,?,?::timestamptz,?::timestamptz,?::timestamptz,?::timestamptz)
                """, UUID.randomUUID(), accountId, hasher.sha256(token), now.toString(), now.toString(),
                now.plus(30, ChronoUnit.DAYS).toString(), now.plus(90, ChronoUnit.DAYS).toString());
        return new Session(createdMemberId, token);
    }

    private jakarta.servlet.http.Cookie cookie(String token) {
        return new jakarta.servlet.http.Cookie("STOCKET_SESSION", token);
    }

    private record Session(UUID memberId, String token) {
    }
}
