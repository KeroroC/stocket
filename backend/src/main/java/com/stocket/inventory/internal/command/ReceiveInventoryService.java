package com.stocket.inventory.internal.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.stocket.catalog.CatalogInventoryItem;
import com.stocket.catalog.CatalogInventoryQuery;
import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.identity.RequestContext;
import com.stocket.audit.AuditEvent;
import com.stocket.inventory.InventoryChanged;
import com.stocket.inventory.internal.domain.AssetDetail;
import com.stocket.inventory.internal.domain.AssetStatus;
import com.stocket.inventory.internal.domain.BatchDetail;
import com.stocket.inventory.internal.domain.ExpirationCalculator;
import com.stocket.inventory.internal.domain.InventoryEntry;
import com.stocket.inventory.internal.domain.InventoryMovement;
import com.stocket.inventory.internal.domain.InventoryType;
import com.stocket.inventory.internal.domain.MovementDraft;
import com.stocket.inventory.internal.domain.MovementType;
import com.stocket.inventory.internal.domain.Quantity;
import com.stocket.inventory.internal.domain.ShelfLife;
import com.stocket.inventory.internal.domain.ShelfLifeUnit;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import com.stocket.inventory.internal.persistence.InventoryEntryRepository;
import com.stocket.inventory.internal.persistence.InventoryMovementRepository;
import com.stocket.inventory.internal.web.InventoryCommandResponse;
import com.stocket.location.LocationInventoryQuery;
import com.stocket.location.LocationSummary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiveInventoryService {

    private static final String OPERATION = "RECEIVE";

    private final CurrentHouseholdProvider currentHousehold;
    private final CatalogInventoryQuery catalog;
    private final LocationInventoryQuery locations;
    private final ExpirationCalculator expirationCalculator;
    private final InventoryEntryRepository entries;
    private final InventoryMovementRepository movements;
    private final IdempotentExecutor idempotency;
    private final ApplicationEventPublisher events;

    public ReceiveInventoryService(CurrentHouseholdProvider currentHousehold,
                                   CatalogInventoryQuery catalog,
                                   LocationInventoryQuery locations,
                                   ExpirationCalculator expirationCalculator,
                                   InventoryEntryRepository entries,
                                   InventoryMovementRepository movements,
                                   IdempotentExecutor idempotency,
                                   ApplicationEventPublisher events) {
        this.currentHousehold = currentHousehold;
        this.catalog = catalog;
        this.locations = locations;
        this.expirationCalculator = expirationCalculator;
        this.entries = entries;
        this.movements = movements;
        this.idempotency = idempotency;
        this.events = events;
    }

    @Transactional
    public IdempotentExecutor.Result<InventoryCommandResponse> receive(
            String idempotencyKey, ReceiveInventoryCommand request) {
        CurrentHousehold current = currentHousehold.requireCurrent();
        ReceiveInventoryCommand command = normalize(request);
        return idempotency.execute(
                new IdempotentExecutor.Context(current.householdId(), current.accountId()),
                OPERATION, idempotencyKey, command, InventoryCommandResponse.class,
                recordId -> executeReceive(current, command, recordId));
    }

    private IdempotentExecutor.Result<InventoryCommandResponse> executeReceive(
            CurrentHousehold current, ReceiveInventoryCommand command, UUID idempotencyRecordId) {
        CatalogInventoryItem item = catalog.find(current.householdId(), command.itemId())
                .orElseThrow(() -> new InventoryCommandException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND"));
        if (item.archived()) {
            throw new InventoryCommandException(HttpStatus.CONFLICT, "ITEM_ARCHIVED");
        }
        LocationSummary location = locations.find(current.householdId(), command.locationId())
                .orElseThrow(() -> new InventoryCommandException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND"));
        if (location.archived()) {
            throw new InventoryCommandException(HttpStatus.CONFLICT, "LOCATION_ARCHIVED");
        }

        Quantity quantity = Quantity.of(command.quantity());
        ShelfLife requestedShelfLife = shelfLife(command.shelfLifeValue(), command.shelfLifeUnit());
        ShelfLife catalogShelfLife = shelfLife(item.defaultShelfLifeValue(), item.defaultShelfLifeUnit());
        LocalDate expirationDate = expirationCalculator.calculate(
                command.expirationDate(), command.productionDate(), requestedShelfLife, catalogShelfLife)
                .orElse(null);
        Instant now = Instant.now();
        UUID entryId = UUID.randomUUID();
        InventoryEntry entry = InventoryEntry.receive(
                entryId, current.householdId(), command.itemId(), command.locationId(), command.type(),
                quantity, command.receivedAt(), command.productionDate(), expirationDate,
                command.customAttributes(), now);
        attachDetail(entry, current.householdId(), command, requestedShelfLife);

        MovementDraft draft = new MovementDraft(
                MovementType.RECEIVE, quantity.value(), BigDecimal.ZERO, quantity.value(),
                null, command.locationId(), null);
        String requestId = RequestContext.requireRequestId();
        InventoryMovement movement = new InventoryMovement(
                UUID.randomUUID(), current.householdId(), entryId, null, draft,
                current.accountId(), idempotencyRecordId, requestId, now);
        try {
            entries.save(entry);
            movements.saveAndFlush(movement);
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(exception, "uq_asset_number")) {
                throw new InventoryCommandException(HttpStatus.CONFLICT, "ASSET_NUMBER_CONFLICT");
            }
            throw exception;
        }

        events.publishEvent(new InventoryChanged(
                UUID.randomUUID(), current.householdId(), command.itemId(), entryId,
                OPERATION, quantity.value(), now, requestId));
        events.publishEvent(new AuditEvent(UUID.randomUUID(), current.householdId(), now, "InventoryReceived", "SUCCESS",
                current.accountId(), "INVENTORY_ENTRY", entryId, requestId, "api", Map.of(
                        "entryId", entryId.toString(), "itemId", command.itemId().toString(),
                        "locationId", command.locationId().toString(), "quantity", decimal(quantity.value()),
                        "type", command.type().name())));
        InventoryCommandResponse response = new InventoryCommandResponse(
                entryId, command.type(), decimal(quantity.value()), command.locationId(), expirationDate,
                entry.assetStatus().orElse(null), entry.version(), requestId);
        return new IdempotentExecutor.Result<>(HttpStatus.CREATED.value(), response);
    }

    private ReceiveInventoryCommand normalize(ReceiveInventoryCommand request) {
        if (request == null || request.itemId() == null || request.type() == null
                || request.locationId() == null || request.receivedAt() == null) {
            throw new InventoryCommandException(HttpStatus.UNPROCESSABLE_ENTITY, "INVENTORY_INVALID");
        }
        Quantity quantity = Quantity.of(request.quantity());
        if ((request.shelfLifeValue() == null) != (request.shelfLifeUnit() == null)) {
            throw new InventoryCommandException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SHELF_LIFE");
        }
        if (request.type() == InventoryType.ASSET
                && (request.assetNumber() == null || request.assetNumber().isBlank())) {
            throw new InventoryCommandException(HttpStatus.UNPROCESSABLE_ENTITY, "ASSET_NUMBER_REQUIRED");
        }
        if (request.type() == InventoryType.BATCH && request.assetNumber() != null) {
            throw new InventoryCommandException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BATCH_DETAIL");
        }
        return new ReceiveInventoryCommand(
                request.itemId(), request.type(), decimal(quantity.value()), request.locationId(),
                request.receivedAt(), request.productionDate(), request.expirationDate(),
                request.shelfLifeValue(), request.shelfLifeUnit(), clean(request.batchNumber()),
                clean(request.assetNumber()), clean(request.serialNumber()), request.purchaseDate(),
                request.warrantyExpiresOn(), request.customAttributes() == null ? Map.of() : request.customAttributes());
    }

    private void attachDetail(InventoryEntry entry, UUID householdId,
                              ReceiveInventoryCommand command, ShelfLife shelfLife) {
        if (command.type() == InventoryType.BATCH) {
            BatchDetail detail = BatchDetail.create(entry, command.batchNumber(), null, shelfLife);
            entry.attachBatch(detail);
            return;
        }
        AssetDetail detail = AssetDetail.create(
                entry, householdId, command.assetNumber(), command.serialNumber(),
                command.purchaseDate(), command.warrantyExpiresOn(), AssetStatus.AVAILABLE);
        entry.attachAsset(detail);
    }

    private ShelfLife shelfLife(Integer value, ShelfLifeUnit unit) {
        return value == null ? null : new ShelfLife(value, unit);
    }

    private ShelfLife shelfLife(Integer value, String unit) {
        return value == null ? null : new ShelfLife(value, ShelfLifeUnit.valueOf(unit));
    }

    private String decimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private boolean containsConstraint(Throwable error, String constraint) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

}
