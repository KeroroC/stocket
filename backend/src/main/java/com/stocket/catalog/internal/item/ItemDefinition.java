package com.stocket.catalog.internal.item;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "item_definition")
class ItemDefinition {
    @Id private UUID id;
    @Column(name = "household_id", nullable = false) private UUID householdId;
    @Column(name = "category_id") private UUID categoryId;
    @Column(nullable = false) private String name;
    @Column(name = "normalized_name", nullable = false) private String normalizedName;
    private String brand;
    private String model;
    private String specification;
    @Column(name = "default_unit", nullable = false) private String defaultUnit;
    @Column(name = "default_shelf_life_value") private Integer defaultShelfLifeValue;
    @Enumerated(EnumType.STRING)
    @Column(name = "default_shelf_life_unit") private ShelfLifeUnit defaultShelfLifeUnit;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_attributes", nullable = false, columnDefinition = "jsonb")
    private Map<String, JsonNode> customAttributes;
    @Version @Column(nullable = false) private long version;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @OneToMany(mappedBy = "itemDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemBarcode> barcodes = new ArrayList<>();
    @OneToMany(mappedBy = "itemDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemTag> tags = new ArrayList<>();

    protected ItemDefinition() {
    }

    ItemDefinition(UUID id, UUID householdId, UUID categoryId, String name, String normalizedName,
                   ItemRequest request, Map<String, JsonNode> attributes, Instant now) {
        this.id = id;
        this.householdId = householdId;
        this.categoryId = categoryId;
        apply(name, normalizedName, request, attributes, now);
        this.createdAt = now;
    }

    void update(String name, String normalizedName, ItemRequest request,
                Map<String, JsonNode> attributes, Instant now) {
        categoryId = request.categoryId();
        apply(name, normalizedName, request, attributes, now);
    }

    private void apply(String name, String normalizedName, ItemRequest request,
                       Map<String, JsonNode> attributes, Instant now) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.brand = request.brand();
        this.model = request.model();
        this.specification = request.specification();
        this.defaultUnit = request.defaultUnit().strip();
        this.defaultShelfLifeValue = request.defaultShelfLifeValue();
        this.defaultShelfLifeUnit = request.defaultShelfLifeUnit();
        this.customAttributes = Map.copyOf(attributes);
        this.updatedAt = now;
    }

    void replaceBarcodes(List<ItemBarcode> replacements) { barcodes.clear(); barcodes.addAll(replacements); }
    void replaceTags(List<ItemTag> replacements) { tags.clear(); tags.addAll(replacements); }
    void archive(Instant now) { archivedAt = now; updatedAt = now; }
    void restore(Instant now) { archivedAt = null; updatedAt = now; }
    UUID id() { return id; }
    UUID householdId() { return householdId; }
    UUID categoryId() { return categoryId; }
    String name() { return name; }
    String brand() { return brand; }
    String model() { return model; }
    String specification() { return specification; }
    String defaultUnit() { return defaultUnit; }
    Integer defaultShelfLifeValue() { return defaultShelfLifeValue; }
    ShelfLifeUnit defaultShelfLifeUnit() { return defaultShelfLifeUnit; }
    Map<String, JsonNode> customAttributes() { return Map.copyOf(customAttributes); }
    List<ItemBarcode> barcodes() { return List.copyOf(barcodes); }
    List<ItemTag> tags() { return List.copyOf(tags); }
    long version() { return version; }
    boolean archived() { return archivedAt != null; }
}
