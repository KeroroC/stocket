package com.stocket.inventory.internal.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryEntryTest {

    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    @Test
    void consumesBatchQuantityAndReturnsBeforeAfterMovementDraft() {
        InventoryEntry entry = receive(InventoryType.BATCH, "2");

        MovementDraft draft = entry.consume(Quantity.of("0.5"), NOW.plusSeconds(1));

        assertThat(entry.availableQuantity()).isEqualByComparingTo("1.5");
        assertThat(draft.type()).isEqualTo(MovementType.CONSUME);
        assertThat(draft.quantityDelta()).isEqualByComparingTo("-0.5");
        assertThat(draft.beforeQuantity()).isEqualByComparingTo("2");
        assertThat(draft.afterQuantity()).isEqualByComparingTo("1.5");
    }

    @Test
    void rejectsAssetReceiveQuantityOtherThanOne() {
        assertThatThrownBy(() -> receive(InventoryType.ASSET, "2"))
                .isInstanceOf(InventoryRuleViolationException.class)
                .extracting(exception -> ((InventoryRuleViolationException) exception).code())
                .isEqualTo("INVALID_ASSET_QUANTITY");
    }

    @Test
    void lostAssetMovesQuantityToZeroAndChangesStatus() {
        InventoryEntry entry = receive(InventoryType.ASSET, "1");
        entry.attachAsset(AssetDetail.create(entry, entry.householdId(), "ASSET-001",
                null, null, null, AssetStatus.AVAILABLE));

        MovementDraft draft = entry.markLost("遗失", NOW.plusSeconds(1));

        assertThat(entry.availableQuantity()).isEqualByComparingTo("0");
        assertThat(entry.assetStatus()).contains(AssetStatus.LOST);
        assertThat(draft.type()).isEqualTo(MovementType.LOSS);
        assertThat(draft.quantityDelta()).isEqualByComparingTo("-1");
    }

    @Test
    void archivedEntryRejectsOperations() {
        InventoryEntry entry = receive(InventoryType.BATCH, "1");
        entry.archive(NOW.plusSeconds(1));

        assertThatThrownBy(() -> entry.consume(Quantity.of("1"), NOW.plusSeconds(2)))
                .isInstanceOf(InventoryRuleViolationException.class)
                .extracting(exception -> ((InventoryRuleViolationException) exception).code())
                .isEqualTo("ENTRY_ARCHIVED");
    }

    private InventoryEntry receive(InventoryType type, String quantity) {
        return InventoryEntry.receive(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                type, Quantity.of(quantity), NOW, null, null, Map.of(), NOW);
    }
}
