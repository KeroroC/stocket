package com.stocket.reminder;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReminderNotificationSchemaTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired DataSource dataSource;
    JdbcTemplate jdbc;
    UUID householdId;
    UUID itemId;
    UUID memberId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into user_account(id,username,normalized_username,display_name,password_hash,status,
                    must_change_password,credentials_changed_at,created_at,updated_at,version)
                values (?,?,?,'管理员','hash','ACTIVE',false,now(),now(),now(),0)
                """, accountId, "schema-admin", "schema-admin");
        jdbc.update("""
                insert into household_member(id,household_id,account_id,role,created_at,updated_at)
                values (?,?,?,'ADMIN',now(),now())
                """, memberId, householdId, accountId);
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes)
                values (?,?,'牛奶','牛奶','盒','{}'::jsonb)
                """, itemId, householdId);
    }

    @Test
    void createsReminderNotificationAndEventPublicationTables() {
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema='public' and table_name in (
                    'reminder_rule','reminder','notification_channel','push_subscription',
                    'notification_delivery','event_publication')
                order by table_name
                """, String.class)).containsExactly(
                "event_publication", "notification_channel", "notification_delivery",
                "push_subscription", "reminder", "reminder_rule");
    }

    @Test
    void preventsDuplicateActiveRemindersAndDeliveries() {
        UUID reminderId = insertReminder();
        assertThatThrownBy(this::insertReminder).isInstanceOf(DataAccessException.class);

        insertDelivery(reminderId, "reminder:member:IN_APP");
        assertThatThrownBy(() -> insertDelivery(reminderId, "reminder:member:IN_APP"))
                .isInstanceOf(DataAccessException.class);
    }

    private UUID insertReminder() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into reminder(id,household_id,item_definition_id,reminder_type,trigger_key,
                    trigger_at,status,created_at,updated_at)
                values (?,?,?,'LOW_STOCK','threshold:1',now(),'OPEN',now(),now())
                """, id, householdId, itemId);
        return id;
    }

    private void insertDelivery(UUID reminderId, String dedupeKey) {
        jdbc.update("""
                insert into notification_delivery(id,household_id,reminder_id,member_id,channel_type,
                    dedupe_key,status,attempt_count,next_attempt_at,created_at,updated_at)
                values (?,?,?,?,'IN_APP',?,'PENDING',0,now(),now(),now())
                """, UUID.randomUUID(), householdId, reminderId, memberId, dedupeKey);
    }
}
