package com.stocket.reminder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stocket.inventory.InventoryChanged;
import com.stocket.reminder.internal.lifecycle.ReminderRecalculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventPublicationRecoveryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired DataSource dataSource;
    @Autowired ApplicationEventPublisher events;
    @Autowired IncompleteEventPublications incompletePublications;
    @Autowired TransactionTemplate transactions;
    @MockitoSpyBean ReminderRecalculator recalculator;

    JdbcTemplate jdbc;
    UUID householdId;
    UUID itemId;
    UUID entryId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");
        householdId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        entryId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'王家','Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into item_definition(id,household_id,name,normalized_name,default_unit,custom_attributes)
                values (?,?,'牛奶','牛奶','盒','{}'::jsonb)
                """, itemId, householdId);
        jdbc.update("""
                insert into location(id,household_id,name,normalized_name,public_code)
                values (?,?,'冰箱','冰箱',?)
                """, locationId, householdId, UUID.randomUUID().toString());
        jdbc.update("""
                insert into reminder_rule(id,household_id,scope_type,expiration_offsets)
                values (?,?,'HOUSEHOLD',array[0])
                """, UUID.randomUUID(), householdId);
        jdbc.update("""
                insert into inventory_entry(id,household_id,item_definition_id,location_id,inventory_type,
                    available_quantity,received_at,expiration_date,custom_attributes)
                values (?,?,?,?,'BATCH',5,now(),?,'{}'::jsonb)
                """, entryId, householdId, itemId, locationId, LocalDate.now().plusDays(1));
    }

    @Test
    void keepsFailedPublicationAndCreatesReminderExactlyOnceAfterResubmission() throws Exception {
        doThrow(new IllegalStateException("first attempt fails"))
                .doCallRealMethod()
                .when(recalculator).recalculate(any(), any(), any());

        transactions.executeWithoutResult(status -> {
            jdbc.update("update inventory_entry set available_quantity=4 where id=?", entryId);
            events.publishEvent(new InventoryChanged(
                    UUID.randomUUID(), householdId, itemId, entryId,
                    "ADJUST", BigDecimal.ONE.negate(), Instant.now()));
        });

        await(() -> incompletePublicationCount() == 1);
        assertThat(jdbc.queryForObject(
                "select available_quantity from inventory_entry where id=?",
                BigDecimal.class, entryId)).isEqualByComparingTo("4");
        assertThat(reminderCount()).isZero();

        doCallRealMethod().when(recalculator).recalculate(any(), any(), any());
        incompletePublications.resubmitIncompletePublications(event -> true);

        await(() -> completedPublicationCount() == 1 && reminderCount() == 1);
        incompletePublications.resubmitIncompletePublications(event -> true);
        assertThat(reminderCount()).isOne();
    }

    private int incompletePublicationCount() {
        return jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is null",
                Integer.class);
    }

    private int completedPublicationCount() {
        return jdbc.queryForObject(
                "select count(*) from event_publication where completion_date is not null",
                Integer.class);
    }

    private int reminderCount() {
        return jdbc.queryForObject("select count(*) from reminder", Integer.class);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
