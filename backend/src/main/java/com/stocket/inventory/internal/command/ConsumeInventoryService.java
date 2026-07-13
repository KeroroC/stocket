package com.stocket.inventory.internal.command;

import java.math.BigDecimal;
import java.util.UUID;

import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.inventory.internal.domain.InventoryType;
import com.stocket.inventory.internal.domain.Quantity;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import com.stocket.inventory.internal.persistence.InventoryMovementRepository;
import com.stocket.inventory.internal.web.InventoryCommandResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumeInventoryService {

    private final CurrentHouseholdProvider currentHousehold;
    private final InventoryOperationExecutor operations;
    private final InventoryMovementRepository movements;

    public ConsumeInventoryService(CurrentHouseholdProvider currentHousehold,
                                   InventoryOperationExecutor operations,
                                   InventoryMovementRepository movements) {
        this.currentHousehold = currentHousehold;
        this.operations = operations;
        this.movements = movements;
    }

    @Transactional
    public IdempotentExecutor.Result<InventoryCommandResponse> consume(
            UUID entryId, String key, String quantityValue) {
        Quantity quantity = Quantity.of(quantityValue);
        CurrentHousehold current = currentHousehold.requireCurrent();
        QuantityRequest request = new QuantityRequest(decimal(quantity.value()), null);
        return operations.execute(current, "CONSUME", key, entryId, request,
                (entry, now) -> entry.consume(quantity, now));
    }

    @Transactional
    public IdempotentExecutor.Result<InventoryCommandResponse> returnInventory(
            UUID entryId, String key, String quantityValue, String reasonValue) {
        CurrentHousehold current = currentHousehold.requireCurrent();
        String reason = reason(reasonValue, null);
        Quantity quantity = quantityValue == null ? null : Quantity.of(quantityValue);
        QuantityRequest request = new QuantityRequest(
                quantity == null ? null : decimal(quantity.value()), reason);
        return operations.execute(current, "RETURN", key, entryId, request, (entry, now) -> {
            if (entry.inventoryType() == InventoryType.ASSET) {
                if (quantity != null && quantity.value().compareTo(BigDecimal.ONE) != 0) {
                    throw new InventoryCommandException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ASSET_QUANTITY");
                }
                return entry.returnToStock(reason, now);
            }
            if (quantity == null) {
                throw new InventoryCommandException(HttpStatus.UNPROCESSABLE_ENTITY, "QUANTITY_REQUIRED");
            }
            BigDecimal outstanding = movements.outstandingConsumed(entry.id());
            if (outstanding.compareTo(quantity.value()) < 0) {
                throw new InventoryCommandException(HttpStatus.CONFLICT, "RETURN_EXCEEDS_CONSUMED");
            }
            return entry.returnBatch(quantity, reason, now);
        });
    }

    static String reason(String value, String requiredCode) {
        String cleaned = value == null ? null : value.strip();
        if (cleaned != null && cleaned.isEmpty()) {
            cleaned = null;
        }
        if (requiredCode != null && cleaned == null) {
            throw new InventoryCommandException(HttpStatus.UNPROCESSABLE_ENTITY, requiredCode);
        }
        if (cleaned != null && cleaned.length() > 240) {
            throw new InventoryCommandException(HttpStatus.UNPROCESSABLE_ENTITY, "REASON_INVALID");
        }
        return cleaned;
    }

    static String decimal(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }

    private record QuantityRequest(String quantity, String reason) {
    }
}
