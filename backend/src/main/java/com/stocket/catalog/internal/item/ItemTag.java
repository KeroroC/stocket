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
@Table(name = "item_tag")
class ItemTag {
    @Id private UUID id;
    @Column(name = "household_id", nullable = false) private UUID householdId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_definition_id", nullable = false)
    private ItemDefinition itemDefinition;
    @Column(nullable = false) private String value;
    @Column(name = "normalized_value", nullable = false) private String normalizedValue;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ItemTag() {
    }

    ItemTag(UUID householdId, ItemDefinition itemDefinition, String value, String normalizedValue, Instant now) {
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.itemDefinition = itemDefinition;
        this.value = value;
        this.normalizedValue = normalizedValue;
        this.createdAt = now;
        this.updatedAt = now;
    }

    String value() { return value; }
}
