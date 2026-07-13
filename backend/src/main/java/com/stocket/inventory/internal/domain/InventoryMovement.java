package com.stocket.inventory.internal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_movement")
public class InventoryMovement {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "entry_id", nullable = false)
    private UUID entryId;

    @Column(name = "related_entry_id")
    private UUID relatedEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 24)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityDelta;

    @Column(name = "from_location_id")
    private UUID fromLocationId;

    @Column(name = "to_location_id")
    private UUID toLocationId;

    @Column(length = 240)
    private String reason;

    @Column(name = "actor_account_id", nullable = false)
    private UUID actorAccountId;

    @Column(name = "idempotency_record_id", nullable = false)
    private UUID idempotencyRecordId;

    @Column(name = "request_id", nullable = false, length = 80)
    private String requestId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(UUID id, UUID householdId, UUID entryId, UUID relatedEntryId,
                             MovementDraft draft, UUID actorAccountId,
                             UUID idempotencyRecordId, String requestId, Instant occurredAt) {
        this.id = id;
        this.householdId = householdId;
        this.entryId = entryId;
        this.relatedEntryId = relatedEntryId;
        this.movementType = draft.type();
        this.quantityDelta = draft.quantityDelta();
        this.fromLocationId = draft.fromLocationId();
        this.toLocationId = draft.toLocationId();
        this.reason = draft.reason();
        this.actorAccountId = actorAccountId;
        this.idempotencyRecordId = idempotencyRecordId;
        this.requestId = requestId;
        this.occurredAt = occurredAt;
    }

    public UUID id() { return id; }
    public UUID entryId() { return entryId; }
    public MovementType movementType() { return movementType; }
    public BigDecimal quantityDelta() { return quantityDelta; }
}
