package com.stocket.inventory;

import java.sql.Timestamp;
import java.time.Instant;
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
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class InventorySchemaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.5-alpine");

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID householdId;
    private UUID accountId;
    private UUID itemId;
    private UUID locationId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("truncate user_account, household cascade");

        householdId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        locationId = UUID.randomUUID();

        jdbc.update("insert into household(id, singleton_key, name, timezone) values (?, 1, '家', 'Asia/Shanghai')",
                householdId);
        jdbc.update("""
                insert into user_account(id, username, normalized_username, display_name, password_hash,
                    status, must_change_password, credentials_changed_at)
                values (?, 'owner', 'owner', 'Owner', 'not-used', 'ACTIVE', false, now())
                """, accountId);
        jdbc.update("""
                insert into item_definition(id, household_id, name, normalized_name, default_unit, custom_attributes)
                values (?, ?, '牛奶', '牛奶', '盒', '{}'::jsonb)
                """, itemId, householdId);
        jdbc.update("""
                insert into location(id, household_id, name, normalized_name, public_code)
                values (?, ?, '冰箱', '冰箱', 'location-code')
                """, locationId, householdId);
    }

    @Test
    void createsInventoryLedgerTablesAndIndexes() {
        assertThat(jdbc.queryForList("""
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('inventory_entry', 'batch_detail', 'asset_detail',
                                     'inventory_movement', 'idempotency_record',
                                     'inventory_reconciliation_issue')
                order by table_name
                """, String.class))
                .containsExactly("asset_detail", "batch_detail", "idempotency_record",
                        "inventory_entry", "inventory_movement", "inventory_reconciliation_issue");

        assertThat(jdbc.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and indexname in ('inventory_entry_item_idx', 'inventory_entry_location_idx',
                                    'inventory_entry_expiration_idx', 'inventory_movement_entry_timeline_idx',
                                    'uq_idempotency_account_operation_key', 'uq_asset_number')
                order by indexname
                """, String.class))
                .containsExactly("inventory_entry_expiration_idx", "inventory_entry_item_idx",
                        "inventory_entry_location_idx", "inventory_movement_entry_timeline_idx",
                        "uq_asset_number", "uq_idempotency_account_operation_key");
    }

    @Test
    void rejectsInvalidQuantitiesDuplicateAssetNumbersAndMovementIds() {
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), "BATCH", "-0.0001"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), "ASSET", "2"))
                .isInstanceOf(DataAccessException.class);

        UUID firstAsset = UUID.randomUUID();
        UUID secondAsset = UUID.randomUUID();
        insertEntry(firstAsset, "ASSET", "1");
        insertEntry(secondAsset, "ASSET", "1");
        insertAsset(firstAsset, "ASSET-001");
        assertThatThrownBy(() -> insertAsset(secondAsset, "ASSET-001"))
                .isInstanceOf(DataAccessException.class);

        UUID batchEntry = UUID.randomUUID();
        insertEntry(batchEntry, "BATCH", "1");
        UUID idempotencyId = insertIdempotency();
        UUID movementId = UUID.randomUUID();
        insertMovement(movementId, batchEntry, idempotencyId);
        assertThatThrownBy(() -> insertMovement(movementId, batchEntry, idempotencyId))
                .isInstanceOf(DataAccessException.class);
    }

    private void insertEntry(UUID entryId, String type, String quantity) {
        jdbc.update("""
                insert into inventory_entry(
                    id, household_id, item_definition_id, location_id, inventory_type,
                    available_quantity, received_at, custom_attributes
                ) values (?, ?, ?, ?, ?, ?::numeric, now(), '{}'::jsonb)
                """, entryId, householdId, itemId, locationId, type, quantity);
    }

    private void insertAsset(UUID entryId, String assetNumber) {
        jdbc.update("""
                insert into asset_detail(
                    inventory_entry_id, household_id, asset_number, status
                ) values (?, ?, ?, 'AVAILABLE')
                """, entryId, householdId, assetNumber);
    }

    private UUID insertIdempotency() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into idempotency_record(
                    id, household_id, account_id, operation, idempotency_key, request_hash,
                    status, created_at, expires_at
                ) values (?, ?, ?, 'RECEIVE', ?, repeat('a', 64), 'PROCESSING', now(), now() + interval '1 day')
                """, id, householdId, accountId, UUID.randomUUID().toString());
        return id;
    }

    private void insertMovement(UUID movementId, UUID entryId, UUID idempotencyId) {
        jdbc.update("""
                insert into inventory_movement(
                    id, household_id, entry_id, movement_type, quantity_delta,
                    actor_account_id, idempotency_record_id, request_id, occurred_at
                ) values (?, ?, ?, 'RECEIVE', 1, ?, ?, ?, ?)
                """, movementId, householdId, entryId, accountId, idempotencyId,
                UUID.randomUUID().toString(), Timestamp.from(Instant.now()));
    }
}
