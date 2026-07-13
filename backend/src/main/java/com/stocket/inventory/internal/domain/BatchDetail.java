package com.stocket.inventory.internal.domain;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "batch_detail")
public class BatchDetail {

    @Id
    @Column(name = "inventory_entry_id")
    private UUID inventoryEntryId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "inventory_entry_id")
    private InventoryEntry entry;

    @Column(name = "batch_number", length = 120)
    private String batchNumber;

    @Column(name = "source_entry_id")
    private UUID sourceEntryId;

    @Column(name = "shelf_life_value")
    private Integer shelfLifeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "shelf_life_unit", length = 8)
    private ShelfLifeUnit shelfLifeUnit;

    protected BatchDetail() {
    }

    private BatchDetail(InventoryEntry entry, String batchNumber, UUID sourceEntryId, ShelfLife shelfLife) {
        this.inventoryEntryId = entry.id();
        this.entry = entry;
        this.batchNumber = batchNumber;
        this.sourceEntryId = sourceEntryId;
        this.shelfLifeValue = shelfLife == null ? null : shelfLife.value();
        this.shelfLifeUnit = shelfLife == null ? null : shelfLife.unit();
    }

    public static BatchDetail create(InventoryEntry entry, String batchNumber,
                                     UUID sourceEntryId, ShelfLife shelfLife) {
        return new BatchDetail(entry, batchNumber, sourceEntryId, shelfLife);
    }

    InventoryEntry entry() { return entry; }
    public String batchNumber() { return batchNumber; }
    public UUID sourceEntryId() { return sourceEntryId; }
    public Optional<ShelfLife> shelfLife() {
        return shelfLifeValue == null ? Optional.empty()
                : Optional.of(new ShelfLife(shelfLifeValue, shelfLifeUnit));
    }
}
