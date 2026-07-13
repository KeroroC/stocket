package com.stocket.catalog.internal.item;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_barcode")
class ItemBarcode {
    @Id private UUID id;
    @Column(name = "household_id", nullable = false) private UUID householdId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_definition_id", nullable = false)
    private ItemDefinition itemDefinition;
    @Column(name = "raw_value", nullable = false) private String rawValue;
    @Column(name = "normalized_value", nullable = false) private String normalizedValue;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ItemBarcode() {
    }

    ItemBarcode(UUID householdId, ItemDefinition itemDefinition, String rawValue, String normalizedValue, Instant now) {
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.itemDefinition = itemDefinition;
        this.rawValue = rawValue;
        this.normalizedValue = normalizedValue;
        this.createdAt = now;
        this.updatedAt = now;
    }

    String rawValue() { return rawValue; }
}
