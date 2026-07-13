package com.stocket.catalog.internal.category;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "category")
class Category {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_inventory_type", nullable = false, length = 16)
    private InventoryType defaultInventoryType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attribute_schema", nullable = false, columnDefinition = "jsonb")
    private List<AttributeDefinition> attributeSchema;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    Category(UUID id, UUID householdId, Category parent, String name, String normalizedName,
             InventoryType defaultInventoryType, List<AttributeDefinition> attributeSchema, Instant now) {
        this.id = id;
        this.householdId = householdId;
        this.parent = parent;
        this.name = name;
        this.normalizedName = normalizedName;
        this.defaultInventoryType = defaultInventoryType;
        this.attributeSchema = List.copyOf(attributeSchema);
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(Category parent, String name, String normalizedName, InventoryType inventoryType,
                List<AttributeDefinition> schema, Instant now) {
        this.parent = parent;
        this.name = name;
        this.normalizedName = normalizedName;
        this.defaultInventoryType = inventoryType;
        this.attributeSchema = List.copyOf(schema);
        this.updatedAt = now;
    }

    void archive(Instant now) {
        archivedAt = now;
        updatedAt = now;
    }

    void restore(Instant now) {
        archivedAt = null;
        updatedAt = now;
    }

    UUID id() { return id; }
    UUID householdId() { return householdId; }
    Category parent() { return parent; }
    String name() { return name; }
    String normalizedName() { return normalizedName; }
    InventoryType defaultInventoryType() { return defaultInventoryType; }
    List<AttributeDefinition> attributeSchema() { return List.copyOf(attributeSchema); }
    long version() { return version; }
    boolean archived() { return archivedAt != null; }
}
