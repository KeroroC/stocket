package com.stocket.inventory.internal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inventory_entry")
public class InventoryEntry {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "item_definition_id", nullable = false)
    private UUID itemDefinitionId;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_type", nullable = false, length = 16)
    private InventoryType inventoryType;

    @Column(name = "available_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableQuantity;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_attributes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> customAttributes;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private BatchDetail batchDetail;

    @OneToOne(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    private AssetDetail assetDetail;

    protected InventoryEntry() {
    }

    private InventoryEntry(UUID id, UUID householdId, UUID itemDefinitionId, UUID locationId,
                           InventoryType inventoryType, BigDecimal availableQuantity,
                           Instant receivedAt, LocalDate productionDate, LocalDate expirationDate,
                           Map<String, Object> customAttributes, Instant now) {
        this.id = id;
        this.householdId = householdId;
        this.itemDefinitionId = itemDefinitionId;
        this.locationId = locationId;
        this.inventoryType = inventoryType;
        this.availableQuantity = availableQuantity;
        this.receivedAt = receivedAt;
        this.productionDate = productionDate;
        this.expirationDate = expirationDate;
        this.customAttributes = new LinkedHashMap<>(customAttributes);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static InventoryEntry receive(UUID id, UUID householdId, UUID itemDefinitionId,
                                         UUID locationId, InventoryType inventoryType,
                                         Quantity quantity, Instant receivedAt,
                                         LocalDate productionDate, LocalDate expirationDate,
                                         Map<String, Object> customAttributes, Instant now) {
        if (inventoryType == InventoryType.ASSET && quantity.value().compareTo(BigDecimal.ONE) != 0) {
            throw InventoryRules.violation("INVALID_ASSET_QUANTITY", "Asset receive quantity must be 1");
        }
        return new InventoryEntry(id, householdId, itemDefinitionId, locationId, inventoryType,
                InventoryRules.requireAvailableQuantity(inventoryType, quantity.value()), receivedAt,
                productionDate, expirationDate,
                customAttributes == null ? Map.of() : customAttributes, now);
    }

    public MovementDraft consume(Quantity quantity, Instant now) {
        requireActive();
        if (inventoryType == InventoryType.ASSET) {
            throw InventoryRules.violation("INVALID_ASSET_OPERATION", "Use an asset lifecycle operation");
        }
        BigDecimal before = availableQuantity;
        if (before.compareTo(quantity.value()) < 0) {
            throw InventoryRules.violation("INSUFFICIENT_STOCK", "Insufficient stock");
        }
        availableQuantity = InventoryRules.requireAvailableQuantity(
                inventoryType, before.subtract(quantity.value()));
        updatedAt = now;
        return draft(MovementType.CONSUME, quantity.value().negate(), before, availableQuantity,
                locationId, null, null);
    }

    public MovementDraft transfer(Quantity quantity, UUID targetLocationId, Instant now) {
        requireActive();
        if (locationId.equals(targetLocationId)) {
            throw InventoryRules.violation("SAME_LOCATION", "Source and target locations must differ");
        }
        BigDecimal before = availableQuantity;
        if (before.compareTo(quantity.value()) < 0) {
            throw InventoryRules.violation("INSUFFICIENT_STOCK", "Insufficient stock");
        }
        UUID sourceLocationId = locationId;
        if (before.compareTo(quantity.value()) == 0) {
            locationId = targetLocationId;
            updatedAt = now;
            return draft(MovementType.TRANSFER, BigDecimal.ZERO, before, before,
                    sourceLocationId, targetLocationId, null);
        }
        if (inventoryType == InventoryType.ASSET) {
            throw InventoryRules.violation("INVALID_ASSET_QUANTITY", "Assets can only be transferred whole");
        }
        availableQuantity = InventoryRules.requireAvailableQuantity(
                inventoryType, before.subtract(quantity.value()));
        updatedAt = now;
        return draft(MovementType.TRANSFER_OUT, quantity.value().negate(), before, availableQuantity,
                sourceLocationId, targetLocationId, null);
    }

    public MovementDraft adjust(BigDecimal targetQuantity, String reason, Instant now) {
        requireActive();
        BigDecimal target = InventoryRules.requireAvailableQuantity(inventoryType, targetQuantity);
        BigDecimal before = availableQuantity;
        availableQuantity = target;
        updatedAt = now;
        return draft(MovementType.ADJUSTMENT, target.subtract(before), before, target,
                locationId, locationId, reason);
    }

    public MovementDraft markLost(String reason, Instant now) {
        requireAsset();
        requireActive();
        BigDecimal before = availableQuantity;
        availableQuantity = BigDecimal.ZERO;
        assetDetail.changeStatus(AssetStatus.LOST);
        updatedAt = now;
        return draft(MovementType.LOSS, before.negate(), before, availableQuantity,
                locationId, null, reason);
    }

    public MovementDraft retire(String reason, Instant now) {
        requireAsset();
        requireActive();
        BigDecimal before = availableQuantity;
        availableQuantity = BigDecimal.ZERO;
        assetDetail.changeStatus(AssetStatus.RETIRED);
        updatedAt = now;
        return draft(MovementType.RETIRE, before.negate(), before, availableQuantity,
                locationId, null, reason);
    }

    public MovementDraft returnToStock(String reason, Instant now) {
        requireAsset();
        requireActive();
        BigDecimal before = availableQuantity;
        availableQuantity = BigDecimal.ONE;
        assetDetail.changeStatus(AssetStatus.AVAILABLE);
        updatedAt = now;
        return draft(MovementType.RETURN, BigDecimal.ONE.subtract(before), before, availableQuantity,
                null, locationId, reason);
    }

    public void attachBatch(BatchDetail detail) {
        if (inventoryType != InventoryType.BATCH || detail.entry() != this) {
            throw InventoryRules.violation("INVALID_BATCH_DETAIL", "Batch detail does not match the entry");
        }
        batchDetail = detail;
    }

    public void attachAsset(AssetDetail detail) {
        if (inventoryType != InventoryType.ASSET || detail.entry() != this) {
            throw InventoryRules.violation("INVALID_ASSET_DETAIL", "Asset detail does not match the entry");
        }
        assetDetail = detail;
    }

    public void archive(Instant now) {
        archivedAt = now;
        updatedAt = now;
    }

    private void requireActive() {
        if (archivedAt != null) {
            throw InventoryRules.violation("ENTRY_ARCHIVED", "Archived inventory entry cannot be changed");
        }
    }

    private void requireAsset() {
        if (inventoryType != InventoryType.ASSET || assetDetail == null) {
            throw InventoryRules.violation("ASSET_DETAIL_REQUIRED", "Asset detail is required");
        }
    }

    private MovementDraft draft(MovementType type, BigDecimal delta,
                                BigDecimal before, BigDecimal after,
                                UUID from, UUID to, String reason) {
        return new MovementDraft(type, delta, before, after, from, to, reason);
    }

    public UUID id() { return id; }
    public UUID householdId() { return householdId; }
    public UUID itemDefinitionId() { return itemDefinitionId; }
    public UUID locationId() { return locationId; }
    public InventoryType inventoryType() { return inventoryType; }
    public BigDecimal availableQuantity() { return availableQuantity; }
    public Instant receivedAt() { return receivedAt; }
    public LocalDate productionDate() { return productionDate; }
    public LocalDate expirationDate() { return expirationDate; }
    public Map<String, Object> customAttributes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(customAttributes));
    }
    public long version() { return version; }
    public boolean archived() { return archivedAt != null; }
    public Optional<AssetStatus> assetStatus() {
        return assetDetail == null ? Optional.empty() : Optional.of(assetDetail.status());
    }
    public Optional<BatchDetail> batchDetail() { return Optional.ofNullable(batchDetail); }
    public Optional<AssetDetail> assetDetail() { return Optional.ofNullable(assetDetail); }
}
