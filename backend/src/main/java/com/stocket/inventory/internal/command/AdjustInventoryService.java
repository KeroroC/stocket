package com.stocket.inventory.internal.command;

import java.math.BigDecimal;
import java.util.UUID;

import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.inventory.internal.domain.Quantity;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import com.stocket.inventory.internal.web.InventoryCommandResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdjustInventoryService {

    private final CurrentHouseholdProvider currentHousehold;
    private final InventoryOperationExecutor operations;

    public AdjustInventoryService(CurrentHouseholdProvider currentHousehold,
                                  InventoryOperationExecutor operations) {
        this.currentHousehold = currentHousehold;
        this.operations = operations;
    }

    @Transactional
    public IdempotentExecutor.Result<InventoryCommandResponse> adjust(
            UUID entryId, String key, String targetValue, String reasonValue) {
        BigDecimal target = Quantity.available(targetValue);
        String reason = ConsumeInventoryService.reason(reasonValue, null);
        CurrentHousehold current = currentHousehold.requireCurrent();
        AdjustmentCommand request = new AdjustmentCommand(
                ConsumeInventoryService.decimal(target), reason);
        return operations.execute(current, "ADJUSTMENT", key, entryId, request, (entry, now) -> {
            if (target.compareTo(entry.availableQuantity()) < 0 && reason == null) {
                ConsumeInventoryService.reason(null, "ADJUSTMENT_REASON_REQUIRED");
            }
            return entry.adjust(target, reason, now);
        });
    }

    private record AdjustmentCommand(String targetQuantity, String reason) {
    }
}
