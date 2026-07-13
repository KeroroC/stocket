package com.stocket.inventory.internal.domain;

import java.time.LocalDate;
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
@Table(name = "asset_detail")
public class AssetDetail {

    @Id
    @Column(name = "inventory_entry_id")
    private UUID inventoryEntryId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "inventory_entry_id")
    private InventoryEntry entry;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "asset_number", nullable = false, length = 80)
    private String assetNumber;

    @Column(name = "serial_number", length = 160)
    private String serialNumber;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "warranty_expires_on")
    private LocalDate warrantyExpiresOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetStatus status;

    protected AssetDetail() {
    }

    private AssetDetail(InventoryEntry entry, UUID householdId, String assetNumber,
                        String serialNumber, LocalDate purchaseDate,
                        LocalDate warrantyExpiresOn, AssetStatus status) {
        if (!entry.householdId().equals(householdId)) {
            throw InventoryRules.violation("ASSET_HOUSEHOLD_MISMATCH", "Asset household must match entry household");
        }
        this.inventoryEntryId = entry.id();
        this.entry = entry;
        this.householdId = householdId;
        this.assetNumber = assetNumber;
        this.serialNumber = serialNumber;
        this.purchaseDate = purchaseDate;
        this.warrantyExpiresOn = warrantyExpiresOn;
        this.status = status;
    }

    public static AssetDetail create(InventoryEntry entry, UUID householdId,
                                     String assetNumber, String serialNumber,
                                     LocalDate purchaseDate, LocalDate warrantyExpiresOn,
                                     AssetStatus status) {
        return new AssetDetail(entry, householdId, assetNumber, serialNumber,
                purchaseDate, warrantyExpiresOn, status);
    }

    void changeStatus(AssetStatus status) { this.status = status; }
    InventoryEntry entry() { return entry; }
    public UUID householdId() { return householdId; }
    public String assetNumber() { return assetNumber; }
    public String serialNumber() { return serialNumber; }
    public LocalDate purchaseDate() { return purchaseDate; }
    public LocalDate warrantyExpiresOn() { return warrantyExpiresOn; }
    public AssetStatus status() { return status; }
}
