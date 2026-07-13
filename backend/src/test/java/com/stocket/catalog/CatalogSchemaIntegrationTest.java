package com.stocket.catalog;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@DisabledInAotMode
class CatalogSchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE household CASCADE");
    }

    @Test
    void rejectsDuplicateNormalizedBarcodeWithinTheHousehold() {
        UUID householdId = UUID.randomUUID();
        jdbc.update("insert into household(id, singleton_key, name, timezone) values (?, 1, ?, ?)",
                householdId, "家", "Asia/Shanghai");

        UUID firstItemId = insertItemDefinition(householdId, "牛奶", "牛奶");
        UUID secondItemId = insertItemDefinition(householdId, "酸奶", "酸奶");
        insertBarcode(householdId, firstItemId, "6900000000012", "6900000000012");

        assertThatThrownBy(() -> insertBarcode(
                householdId, secondItemId, "690-0000-0000-12", "6900000000012"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void barcodeUniqueIndexIncludesHouseholdAndNormalizedValue() {
        String indexDefinition = jdbc.queryForObject("""
                select indexdef
                from pg_indexes
                where schemaname = 'public'
                  and tablename = 'item_barcode'
                  and indexname = 'uq_item_barcode_household_normalized_value'
                """, String.class);

        assertThat(indexDefinition)
                .contains("household_id", "normalized_value")
                .containsIgnoringCase("UNIQUE INDEX");
    }

    private UUID insertItemDefinition(UUID householdId, String name, String normalizedName) {
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                insert into item_definition(
                    id, household_id, name, normalized_name, default_unit, custom_attributes
                ) values (?, ?, ?, ?, '件', '{}'::jsonb)
                """, itemId, householdId, name, normalizedName);
        return itemId;
    }

    private void insertBarcode(UUID householdId, UUID itemDefinitionId,
                               String rawValue, String normalizedValue) {
        jdbc.update("""
                insert into item_barcode(
                    id, household_id, item_definition_id, raw_value, normalized_value
                ) values (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), householdId, itemDefinitionId, rawValue, normalizedValue);
    }
}
