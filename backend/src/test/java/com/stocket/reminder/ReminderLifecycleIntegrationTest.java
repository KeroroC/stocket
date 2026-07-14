package com.stocket.reminder;

import java.time.LocalDate;
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

import com.stocket.reminder.internal.lifecycle.ReminderRecalculator;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReminderLifecycleIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired DataSource dataSource;
    @Autowired ReminderRecalculator recalculator;

    JdbcTemplate jdbc;
    UUID householdId;
    UUID categoryId;
    UUID itemId;
    UUID locationId;
    UUID entryId;

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
                insert into reminder_rule(id,household_id,scope_type,expiration_offsets,low_stock_threshold)
                values (?,?,'HOUSEHOLD',array[7,1,0],3)
                """, UUID.randomUUID(), householdId);
        jdbc.update("""
                insert into inventory_entry(id,household_id,item_definition_id,location_id,inventory_type,
                    available_quantity,received_at,expiration_date,custom_attributes)
                values (?,?,?,?,'BATCH',5,now(),?,'{}'::jsonb)
                """, entryId, householdId, itemId, locationId, LocalDate.now().plusDays(10));
    }

    @Test
    void maintainsExpirationAndLowStockReminderLifecycleWithoutDuplicates() {
        recalculator.recalculate(householdId, itemId, entryId);
        assertThat(activeCount("EXPIRING")).isEqualTo(3);
        assertThat(activeCount("LOW_STOCK")).isZero();

        jdbc.update("update inventory_entry set available_quantity=3 where id=?", entryId);
        recalculator.recalculate(householdId, itemId, entryId);
        recalculator.recalculate(householdId, itemId, entryId);
        assertThat(activeCount("LOW_STOCK")).isOne();

        jdbc.update("update inventory_entry set available_quantity=5 where id=?", entryId);
        recalculator.recalculate(householdId, itemId, entryId);
        assertThat(activeCount("LOW_STOCK")).isZero();
        assertThat(statusCount("LOW_STOCK", "RESOLVED")).isOne();

        LocalDate changedExpiration = LocalDate.now().plusDays(20);
        jdbc.update("update inventory_entry set expiration_date=? where id=?", changedExpiration, entryId);
        recalculator.recalculate(householdId, itemId, entryId);

        assertThat(activeCount("EXPIRING")).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                select count(*) from reminder
                where inventory_entry_id=? and reminder_type='EXPIRING' and status='RESOLVED'
                """, Integer.class, entryId)).isEqualTo(3);
        assertThat(jdbc.queryForList("""
                select trigger_key from reminder
                where inventory_entry_id=? and reminder_type='EXPIRING'
                  and status in ('SCHEDULED','OPEN','ACKNOWLEDGED')
                order by trigger_at, trigger_key
                """, String.class, entryId)).allMatch(key -> key.contains(changedExpiration.toString()));
    }

    private int activeCount(String type) {
        return jdbc.queryForObject("""
                select count(*) from reminder
                where household_id=? and item_definition_id=? and reminder_type=?
                  and status in ('SCHEDULED','OPEN','ACKNOWLEDGED')
                """, Integer.class, householdId, itemId, type);
    }

    private int statusCount(String type, String status) {
        return jdbc.queryForObject("""
                select count(*) from reminder
                where household_id=? and item_definition_id=? and reminder_type=? and status=?
                """, Integer.class, householdId, itemId, type, status);
    }
}
