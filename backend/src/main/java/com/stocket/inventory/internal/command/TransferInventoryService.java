package com.stocket.inventory.internal.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.inventory.InventoryChanged;
import com.stocket.inventory.internal.domain.BatchDetail;
import com.stocket.inventory.internal.domain.InventoryEntry;
import com.stocket.inventory.internal.domain.InventoryMovement;
import com.stocket.inventory.internal.domain.InventoryType;
import com.stocket.inventory.internal.domain.MovementDraft;
import com.stocket.inventory.internal.domain.MovementType;
import com.stocket.inventory.internal.domain.Quantity;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import com.stocket.inventory.internal.persistence.InventoryEntryRepository;
import com.stocket.inventory.internal.persistence.InventoryMovementRepository;
import com.stocket.inventory.internal.web.InventoryCommandResponse;
import com.stocket.location.LocationInventoryQuery;
import com.stocket.location.LocationSummary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferInventoryService {

    private static final String OPERATION = "TRANSFER";

    private final CurrentHouseholdProvider currentHousehold;
    private final LocationInventoryQuery locations;
    private final InventoryEntryRepository entries;
    private final InventoryMovementRepository movements;
    private final IdempotentExecutor idempotency;
    private final ApplicationEventPublisher events;

    public TransferInventoryService(CurrentHouseholdProvider currentHousehold,
                                    LocationInventoryQuery locations,
                                    InventoryEntryRepository entries,
                                    InventoryMovementRepository movements,
                                    IdempotentExecutor idempotency,
                                    ApplicationEventPublisher events) {
        this.currentHousehold = currentHousehold;
        this.locations = locations;
        this.entries = entries;
        this.movements = movements;
        this.idempotency = idempotency;
        this.events = events;
    }

    @Transactional
    public IdempotentExecutor.Result<InventoryCommandResponse> transfer(
            UUID sourceEntryId, String key, UUID targetLocationId, String quantityValue) {
        CurrentHousehold current = currentHousehold.requireCurrent();
        Quantity quantity = Quantity.of(quantityValue);
        TransferInventoryCommand command = new TransferInventoryCommand(
                sourceEntryId, targetLocationId, decimal(quantity.value()));
        return idempotency.execute(
                new IdempotentExecutor.Context(current.householdId(), current.accountId()),
                OPERATION, key, command, InventoryCommandResponse.class,
                recordId -> executeTransfer(current, command, quantity, recordId));
    }

    private IdempotentExecutor.Result<InventoryCommandResponse> executeTransfer(
            CurrentHousehold current, TransferInventoryCommand command,
            Quantity quantity, UUID idempotencyRecordId) {
        LocationSummary targetLocation = locations.find(current.householdId(), command.targetLocationId())
                .orElseThrow(() -> new InventoryCommandException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND"));
        if (targetLocation.archived()) {
            throw new InventoryCommandException(HttpStatus.CONFLICT, "LOCATION_ARCHIVED");
        }
        InventoryEntry source = entries.findByHouseholdIdAndIdForUpdate(
                        current.householdId(), command.sourceEntryId())
                .orElseThrow(() -> new InventoryCommandException(
                        HttpStatus.NOT_FOUND, "INVENTORY_ENTRY_NOT_FOUND"));
        Instant now = Instant.now();
        UUID sourceLocationId = source.locationId();
        MovementDraft sourceDraft = source.transfer(quantity, command.targetLocationId(), now);
        String requestId = idempotencyRecordId.toString();

        if (sourceDraft.type() == MovementType.TRANSFER) {
            InventoryMovement movement = movement(
                    source, null, sourceDraft, current, idempotencyRecordId, requestId, now);
            movements.saveAndFlush(movement);
            publish(current, source, sourceDraft.quantityDelta(), now);
            return result(source, requestId);
        }

        BatchDetail sourceBatch = source.batchDetail()
                .orElseThrow(() -> new InventoryCommandException(
                        HttpStatus.CONFLICT, "BATCH_DETAIL_REQUIRED"));
        UUID targetEntryId = UUID.randomUUID();
        InventoryEntry target = InventoryEntry.receive(
                targetEntryId, current.householdId(), source.itemDefinitionId(), command.targetLocationId(),
                InventoryType.BATCH, quantity, source.receivedAt(), source.productionDate(),
                source.expirationDate(), source.customAttributes(), now);
        target.attachBatch(BatchDetail.create(
                target, sourceBatch.batchNumber(), source.id(), sourceBatch.shelfLife().orElse(null)));

        MovementDraft targetDraft = new MovementDraft(
                MovementType.TRANSFER_IN, quantity.value(), BigDecimal.ZERO, quantity.value(),
                sourceLocationId, command.targetLocationId(), null);
        InventoryMovement sourceMovement = movement(
                source, targetEntryId, sourceDraft, current, idempotencyRecordId, requestId, now);
        InventoryMovement targetMovement = movement(
                target, source.id(), targetDraft, current, idempotencyRecordId, requestId, now);
        entries.save(target);
        movements.saveAllAndFlush(java.util.List.of(sourceMovement, targetMovement));
        publish(current, source, sourceDraft.quantityDelta(), now);
        publish(current, target, targetDraft.quantityDelta(), now);
        return result(target, requestId);
    }

    private InventoryMovement movement(InventoryEntry entry, UUID relatedEntryId,
                                       MovementDraft draft, CurrentHousehold current,
                                       UUID idempotencyRecordId, String requestId, Instant now) {
        return new InventoryMovement(
                UUID.randomUUID(), current.householdId(), entry.id(), relatedEntryId, draft,
                current.accountId(), idempotencyRecordId, requestId, now);
    }

    private void publish(CurrentHousehold current, InventoryEntry entry,
                         BigDecimal delta, Instant now) {
        events.publishEvent(new InventoryChanged(
                UUID.randomUUID(), current.householdId(), entry.itemDefinitionId(), entry.id(),
                OPERATION, delta, now));
    }

    private IdempotentExecutor.Result<InventoryCommandResponse> result(
            InventoryEntry entry, String requestId) {
        InventoryCommandResponse response = new InventoryCommandResponse(
                entry.id(), entry.inventoryType(), decimal(entry.availableQuantity()), entry.locationId(),
                entry.expirationDate(), entry.assetStatus().orElse(null), entry.version(), requestId);
        return new IdempotentExecutor.Result<>(HttpStatus.OK.value(), response);
    }

    private String decimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }
}
