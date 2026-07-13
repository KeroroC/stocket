package com.stocket.inventory.internal.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.stocket.identity.CurrentHousehold;
import com.stocket.inventory.InventoryChanged;
import com.stocket.inventory.internal.domain.InventoryEntry;
import com.stocket.inventory.internal.domain.InventoryMovement;
import com.stocket.inventory.internal.domain.MovementDraft;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import com.stocket.inventory.internal.persistence.InventoryEntryRepository;
import com.stocket.inventory.internal.persistence.InventoryMovementRepository;
import com.stocket.inventory.internal.web.InventoryCommandResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InventoryOperationExecutor {

    private final InventoryEntryRepository entries;
    private final InventoryMovementRepository movements;
    private final IdempotentExecutor idempotency;
    private final ApplicationEventPublisher events;

    public InventoryOperationExecutor(InventoryEntryRepository entries,
                                      InventoryMovementRepository movements,
                                      IdempotentExecutor idempotency,
                                      ApplicationEventPublisher events) {
        this.entries = entries;
        this.movements = movements;
        this.idempotency = idempotency;
        this.events = events;
    }

    public IdempotentExecutor.Result<InventoryCommandResponse> execute(
            CurrentHousehold current, String operation, String key, UUID entryId,
            Object request, EntryOperation operationWork) {
        return idempotency.execute(
                new IdempotentExecutor.Context(current.householdId(), current.accountId()),
                operation, key, new OperationRequest(entryId, request), InventoryCommandResponse.class,
                idempotencyRecordId -> apply(
                        current, operation, entryId, idempotencyRecordId, operationWork));
    }

    private IdempotentExecutor.Result<InventoryCommandResponse> apply(
            CurrentHousehold current, String operation, UUID entryId, UUID idempotencyRecordId,
            EntryOperation operationWork) {
        InventoryEntry entry = entries.findByHouseholdIdAndIdForUpdate(current.householdId(), entryId)
                .orElseThrow(() -> new InventoryCommandException(HttpStatus.NOT_FOUND, "INVENTORY_ENTRY_NOT_FOUND"));
        Instant now = Instant.now();
        MovementDraft draft = operationWork.apply(entry, now);
        String requestId = idempotencyRecordId.toString();
        InventoryMovement movement = new InventoryMovement(
                UUID.randomUUID(), current.householdId(), entry.id(), null, draft,
                current.accountId(), idempotencyRecordId, requestId, now);
        movements.saveAndFlush(movement);
        events.publishEvent(new InventoryChanged(
                UUID.randomUUID(), current.householdId(), entry.itemDefinitionId(), entry.id(),
                operation, draft.quantityDelta(), now));
        InventoryCommandResponse response = new InventoryCommandResponse(
                entry.id(), entry.inventoryType(), decimal(entry.availableQuantity()), entry.locationId(),
                entry.expirationDate(), entry.assetStatus().orElse(null), entry.version(), requestId);
        return new IdempotentExecutor.Result<>(HttpStatus.OK.value(), response);
    }

    private String decimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }

    @FunctionalInterface
    public interface EntryOperation {
        MovementDraft apply(InventoryEntry entry, Instant now);
    }

    private record OperationRequest(UUID entryId, Object body) {
    }
}
