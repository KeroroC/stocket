package com.stocket.inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransferInventoryIntegrationTest extends InventoryCommandTestSupport {

    @Test
    void fullyTransfersBatchByChangingItsLocation() throws Exception {
        UUID targetLocation = insertLocation("储物间", false);
        UUID entryId = insertBatch("5");

        var result = transfer(entryId, "full-transfer", targetLocation, "5");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select location_id from inventory_entry where id=?",
                UUID.class, entryId)).isEqualTo(targetLocation);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movement where entry_id=? and movement_type='TRANSFER'",
                Integer.class, entryId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity_delta from inventory_movement where entry_id=? and movement_type='TRANSFER'",
                String.class, entryId)).isEqualTo("0.0000");
    }

    @Test
    void partiallyTransfersBatchIntoTraceableNewEntryWithoutMerging() throws Exception {
        UUID targetLocation = insertLocation("储物间", false);
        UUID sourceId = insertBatch("5");
        jdbc.update("""
                update inventory_entry set production_date='2026-01-31', expiration_date='2026-02-28' where id=?
                """, sourceId);
        jdbc.update("""
                update batch_detail set shelf_life_value=1, shelf_life_unit='MONTH' where inventory_entry_id=?
                """, sourceId);

        var first = transfer(sourceId, "partial-transfer", targetLocation, "2");
        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        var replay = transfer(sourceId, "partial-transfer", targetLocation, "2");
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());

        List<Map<String, Object>> entries = jdbc.queryForList("""
                select e.id,e.available_quantity,e.location_id,e.production_date,e.expiration_date,
                       b.batch_number,b.source_entry_id,b.shelf_life_value,b.shelf_life_unit
                from inventory_entry e join batch_detail b on b.inventory_entry_id=e.id
                where e.item_definition_id=? order by e.id
                """, itemId);
        assertThat(entries).hasSize(2);
        Map<String, Object> target = entries.stream()
                .filter(row -> targetLocation.equals(row.get("location_id"))).findFirst().orElseThrow();
        UUID targetId = (UUID) target.get("id");
        assertThat(target.get("available_quantity").toString()).isEqualTo("2.0000");
        assertThat(target.get("production_date").toString()).isEqualTo("2026-01-31");
        assertThat(target.get("expiration_date").toString()).isEqualTo("2026-02-28");
        assertThat(target.get("batch_number")).isEqualTo("B-001");
        assertThat(target.get("source_entry_id")).isEqualTo(sourceId);
        assertThat(target.get("shelf_life_value")).isEqualTo(1);
        assertThat(target.get("shelf_life_unit")).isEqualTo("MONTH");

        List<Map<String, Object>> transferMovements = jdbc.queryForList("""
                select entry_id,related_entry_id,movement_type,quantity_delta,request_id,idempotency_record_id
                from inventory_movement where movement_type in ('TRANSFER_OUT','TRANSFER_IN') order by movement_type
                """);
        assertThat(transferMovements).hasSize(2);
        assertThat(transferMovements).extracting(row -> row.get("related_entry_id"))
                .containsExactlyInAnyOrder(sourceId, targetId);
        assertThat(transferMovements).extracting(row -> row.get("request_id")).containsOnly(
                transferMovements.getFirst().get("request_id"));
        assertThat(transferMovements).extracting(row -> row.get("idempotency_record_id")).containsOnly(
                transferMovements.getFirst().get("idempotency_record_id"));
        assertThat(jdbc.queryForObject("select sum(available_quantity) from inventory_entry where item_definition_id=?",
                String.class, itemId)).isEqualTo("5.0000");
    }

    @Test
    void rejectsInvalidAssetSameLocationAndArchivedTarget() throws Exception {
        UUID targetLocation = insertLocation("储物间", false);
        UUID assetId = insertAsset();
        var partialAsset = transfer(assetId, "asset-partial", targetLocation, "0.5");
        assertThat(partialAsset.getResponse().getStatus()).isEqualTo(422);
        assertThat(partialAsset.getResponse().getContentAsString()).contains("INVALID_ASSET_QUANTITY");
        var wholeAsset = transfer(assetId, "asset-whole", targetLocation, "1");
        assertThat(wholeAsset.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select location_id from inventory_entry where id=?",
                UUID.class, assetId)).isEqualTo(targetLocation);

        UUID batchId = insertBatch("2");
        var sameLocation = transfer(batchId, "same-location", locationId, "2");
        assertThat(sameLocation.getResponse().getStatus()).isEqualTo(409);
        assertThat(sameLocation.getResponse().getContentAsString()).contains("SAME_LOCATION");

        UUID archived = insertLocation("已归档", true);
        var archivedTarget = transfer(batchId, "archived-target", archived, "2");
        assertThat(archivedTarget.getResponse().getStatus()).isEqualTo(409);
        assertThat(archivedTarget.getResponse().getContentAsString()).contains("LOCATION_ARCHIVED");
    }

    @Test
    void partialTransferNeverMergesIntoAnExistingTargetBatch() throws Exception {
        UUID targetLocation = insertLocation("储物间", false);
        UUID existingTarget = insertBatch("1");
        jdbc.update("update inventory_entry set location_id=? where id=?", targetLocation, existingTarget);
        UUID sourceId = insertBatch("5");

        assertThat(transfer(sourceId, "no-auto-merge", targetLocation, "2")
                .getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbc.queryForObject("""
                select count(*) from inventory_entry
                where item_definition_id=? and location_id=? and archived_at is null
                """, Integer.class, itemId, targetLocation)).isEqualTo(2);
    }

    private org.springframework.test.web.servlet.MvcResult transfer(
            UUID entryId, String key, UUID targetLocation, String quantity) throws Exception {
        return command("/api/v1/inventory/entries/" + entryId + "/transfer", key,
                "{\"targetLocationId\":\"%s\",\"quantity\":\"%s\"}"
                        .formatted(targetLocation, quantity));
    }

    private UUID insertLocation(String name, boolean archived) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into location(id,household_id,name,normalized_name,public_code,archived_at)
                values (?,?,?,?,?,case when ? then now() else null end)
                """, id, householdId, name, name, UUID.randomUUID().toString(), archived);
        return id;
    }
}
