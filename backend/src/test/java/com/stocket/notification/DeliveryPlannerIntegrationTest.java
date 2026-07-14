package com.stocket.notification;

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
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.notification.internal.delivery.DeliveryPlanner;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "STOCKET_MASTER_KEY=QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
        "stocket.notification.worker-enabled=false"
})
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeliveryPlannerIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired DataSource dataSource;
    @Autowired DeliveryPlanner planner;

    JdbcTemplate jdbc;
    UUID householdId;
    UUID reminderId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        reminderId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes)
                values (?,?,'牛奶','牛奶','盒','{}'::jsonb)
                """, itemId, householdId);
        insertMember("member-one", "one@example.com");
        insertMember("member-two", "two@example.com");
        jdbc.update("""
                insert into reminder(id,household_id,item_definition_id,reminder_type,trigger_key,
                    trigger_at,status,opened_at,created_at,updated_at)
                values (?,?,?,'LOW_STOCK','LOW_STOCK:2',now(),'OPEN',now(),now(),now())
                """, reminderId, householdId, itemId);
        insertChannel("IN_APP");
        insertChannel("WEBHOOK");
    }

    @Test
    void expandsPerMemberAndChannelWithoutDuplicatingRepeatedEvents() {
        NotificationRequested event = new NotificationRequested(reminderId, householdId, Instant.now());

        planner.plan(event);
        planner.plan(event);

        assertThat(jdbc.queryForObject("select count(*) from notification_delivery", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(distinct dedupe_key) from notification_delivery",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("""
                select channel_type from notification_delivery order by channel_type,member_id
                """, String.class)).containsExactly("IN_APP", "IN_APP", "WEBHOOK", "WEBHOOK");
    }

    private void insertMember(String username, String email) {
        UUID accountId = UUID.randomUUID();
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,email,password_hash,status,
                    credentials_changed_at,created_at,updated_at)
                values (?,?,?,?,?,'hash','ACTIVE',now(),now(),now())
                """, accountId, username, username, username, email);
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,'MEMBER',now(),now())
                """, UUID.randomUUID(), householdId, accountId);
    }

    private void insertChannel(String type) {
        jdbc.update("""
                insert into notification_channel(id,household_id,type,enabled,configuration_json)
                values (?,?,?,true,'{}'::jsonb)
                """, UUID.randomUUID(), householdId, type);
    }
}
