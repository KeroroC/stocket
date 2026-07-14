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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
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
        assertIndex("uq_item_barcode_household_normalized_value",
                "CREATE UNIQUE INDEX", "USING btree (household_id, normalized_value)");
    }

    @Test
    void createsRequiredTreeAndSearchIndexes() {
        assertIndex("uq_category_active_sibling_name",
                "CREATE UNIQUE INDEX", "USING btree (household_id, parent_id, normalized_name) NULLS NOT DISTINCT",
                "WHERE (archived_at IS NULL)");
        assertIndex("uq_location_active_sibling_name",
                "CREATE UNIQUE INDEX", "USING btree (household_id, parent_id, normalized_name) NULLS NOT DISTINCT",
                "WHERE (archived_at IS NULL)");
        assertIndex("uq_location_household_public_code",
                "CREATE UNIQUE INDEX", "USING btree (household_id, public_code)");
        assertIndex("uq_item_tag_item_normalized_value",
                "CREATE UNIQUE INDEX", "USING btree (item_definition_id, normalized_value)");
        assertIndex("gin_catalog_search_text",
                "USING gin (searchable_text gin_trgm_ops)");
        assertIndex("gin_catalog_search_normalized_barcodes",
                "USING gin (normalized_barcodes)");
        assertIndex("catalog_search_stable_page_idx",
                "USING btree (household_id, archived, display_name, item_definition_id)");
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

    private void assertIndex(String indexName, String... fragments) {
        String indexDefinition = jdbc.queryForObject("""
                select indexdef
                from pg_indexes
                where schemaname = 'public'
                  and indexname = ?
                """, String.class, indexName);

        assertThat(indexDefinition).contains(fragments);
    }
}
