package com.stocket.system;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class UpgradeCompatibilityTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Test void migratesStageSixSchemaForwardWithoutChangingHistoryOrCoreData() {
        Flyway stageSix = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion("6")).load();
        stageSix.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        List<Map<String, Object>> history = jdbc.queryForList("select version, checksum from flyway_schema_history where success order by installed_rank");

        UUID household = UUID.randomUUID(); UUID category = UUID.randomUUID(); UUID location = UUID.randomUUID(); UUID item = UUID.randomUUID(); UUID entry = UUID.randomUUID();
        jdbc.update("insert into household(id,singleton_key,name,timezone) values (?,1,'升级家庭','Asia/Shanghai')", household);
        jdbc.update("insert into category(id,household_id,name,normalized_name,default_inventory_type,attribute_schema) values (?,?, '食品','食品','BATCH','[]'::jsonb)", category, household);
        jdbc.update("insert into location(id,household_id,name,normalized_name,public_code) values (?,?, '冰箱','冰箱','UPGRADE-FRIDGE')", location, household);
        jdbc.update("insert into item_definition(id,household_id,category_id,name,normalized_name,default_unit,custom_attributes) values (?,?,?,'升级牛奶','升级牛奶','盒','{}'::jsonb)", item, household, category);
        jdbc.update("insert into inventory_entry(id,household_id,item_definition_id,location_id,inventory_type,available_quantity,received_at,custom_attributes) values (?,?,?,?, 'BATCH',2,now(),'{}'::jsonb)", entry, household, item, location);
        jdbc.update("insert into batch_detail(inventory_entry_id,batch_number) values (?,'UPGRADE-1')", entry);

        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("select max(version::integer) from flyway_schema_history where success", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select name from item_definition where id=?", String.class, item)).isEqualTo("升级牛奶");
        assertThat(jdbc.queryForObject("select available_quantity from inventory_entry where id=?", java.math.BigDecimal.class, entry)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name='attachment'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("select version, checksum from flyway_schema_history where success and version::integer <= 6 order by installed_rank"))
                .isEqualTo(history);
    }
}
