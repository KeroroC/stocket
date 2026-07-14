package com.stocket.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.notification.internal.channel.WebhookSender;
import com.stocket.notification.internal.worker.DeliveryWorker;
import com.stocket.notification.internal.worker.SendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@Testcontainers
@SpringBootTest(properties = {
        "STOCKET_MASTER_KEY=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "stocket.notification.worker-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryWorkerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired DataSource dataSource;
    @Autowired DeliveryWorker worker;
    @MockitoSpyBean WebhookSender webhookSender;

    JdbcTemplate jdbc;
    UUID householdId;
    UUID reminderId;
    UUID memberId;
    UUID webhookChannelId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        reminderId = UUID.randomUUID();
        webhookChannelId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,email,password_hash,status,
                    credentials_changed_at,created_at,updated_at)
                values (?,'worker','worker','Worker','worker@example.com','hash','ACTIVE',now(),now(),now())
                """, accountId);
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,'MEMBER',now(),now())
                """, memberId, householdId, accountId);
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes)
                values (?,?,'牛奶','牛奶','盒','{}'::jsonb)
                """, itemId, householdId);
        jdbc.update("""
                insert into reminder(id,household_id,item_definition_id,reminder_type,trigger_key,
                    trigger_at,status,opened_at,created_at,updated_at)
                values (?,?,?,'LOW_STOCK','LOW_STOCK:2',now(),'OPEN',now(),now(),now())
                """, reminderId, householdId, itemId);
        jdbc.update("""
                insert into notification_channel(id,household_id,type,enabled,configuration_json)
                values (?,?,'WEBHOOK',true,'{"url":"https://8.8.8.8/hook"}'::jsonb)
                """, webhookChannelId, householdId);
    }

    @Test
    void processesOutsideClaimTransactionRetriesAndKeepsChannelsIndependent() {
        UUID retrying = insertDelivery("WEBHOOK", webhookChannelId, 0);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return SendResult.fromHttp(500, null);
        }).when(webhookSender).send(any());

        assertThat(worker.runOnce()).isTrue();
        assertThat(status(retrying)).isEqualTo("RETRY_WAIT");
        assertThat(attempts(retrying)).isOne();
        assertThat(jdbc.queryForObject("select next_attempt_at is not null from notification_delivery where id=?",
                Boolean.class, retrying)).isTrue();

        jdbc.update("update notification_delivery set next_attempt_at=now() where id=?", retrying);
        doReturn(SendResult.delivered()).when(webhookSender).send(any());
        assertThat(worker.runOnce()).isTrue();
        assertThat(status(retrying)).isEqualTo("DELIVERED");

        UUID permanent = insertDelivery("WEBHOOK", webhookChannelId, 0);
        doReturn(SendResult.fromHttp(400, null)).when(webhookSender).send(any());
        worker.runOnce();
        assertThat(status(permanent)).isEqualTo("DEAD");

        UUID rateLimited = insertDelivery("WEBHOOK", webhookChannelId, 0);
        doReturn(SendResult.fromHttp(429, Duration.ofMinutes(10))).when(webhookSender).send(any());
        Instant before = Instant.now().plus(Duration.ofMinutes(9));
        worker.runOnce();
        assertThat(jdbc.queryForObject("select next_attempt_at from notification_delivery where id=?",
                java.time.OffsetDateTime.class, rateLimited).toInstant()).isAfter(before);

        UUID exhausted = insertDelivery("WEBHOOK", webhookChannelId, 7);
        doReturn(SendResult.fromHttp(500, null)).when(webhookSender).send(any());
        worker.runOnce();
        assertThat(status(exhausted)).isEqualTo("DEAD");
        assertThat(attempts(exhausted)).isEqualTo(8);

        UUID failedWebhook = insertDelivery("WEBHOOK", webhookChannelId, 0);
        UUID inApp = insertDelivery("IN_APP", null, 0);
        doReturn(SendResult.fromHttp(400, null)).when(webhookSender).send(any());
        assertThat(worker.runBatch(2)).isEqualTo(2);
        assertThat(status(failedWebhook)).isEqualTo("DEAD");
        assertThat(status(inApp)).isEqualTo("DELIVERED");
    }

    @Test
    void reclaimsExpiredLeaseWithSameDeliveryIdAndNewOwner() {
        UUID deliveryId = insertDelivery("WEBHOOK", webhookChannelId, 0);

        var first = worker.claimOne().orElseThrow();
        assertThat(first.id()).isEqualTo(deliveryId);
        jdbc.update("update notification_delivery set lease_until=now()-interval '1 second' where id=?",
                deliveryId);

        var reclaimed = worker.claimOne().orElseThrow();
        assertThat(reclaimed.id()).isEqualTo(deliveryId);
        assertThat(reclaimed.leaseOwner()).isNotEqualTo(first.leaseOwner());
        assertThat(reclaimed.deliveryKey()).isEqualTo(deliveryId.toString());
    }

    private UUID insertDelivery(String type, UUID channelId, int attempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into notification_delivery(id,household_id,reminder_id,member_id,channel_type,
                    channel_id,dedupe_key,status,attempt_count,next_attempt_at,created_at,updated_at)
                values (?,?,?,?,?,?,?,'PENDING',?,now(),now(),now())
                """, id, householdId, reminderId, memberId, type, channelId,
                id + ":dedupe", attempts);
        return id;
    }

    private String status(UUID id) {
        return jdbc.queryForObject("select status from notification_delivery where id=?", String.class, id);
    }

    private int attempts(UUID id) {
        return jdbc.queryForObject("select attempt_count from notification_delivery where id=?", Integer.class, id);
    }
}
