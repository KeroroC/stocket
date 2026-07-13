package com.stocket.inventory.internal.command;

import java.util.UUID;

import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import com.stocket.inventory.internal.web.InventoryCommandResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetLifecycleService {

    private final CurrentHouseholdProvider currentHousehold;
    private final InventoryOperationExecutor operations;

    public AssetLifecycleService(CurrentHouseholdProvider currentHousehold,
                                 InventoryOperationExecutor operations) {
        this.currentHousehold = currentHousehold;
        this.operations = operations;
    }

    @Transactional
    public IdempotentExecutor.Result<InventoryCommandResponse> lost(
            UUID entryId, String key, String reasonValue) {
        String reason = ConsumeInventoryService.reason(reasonValue, "REASON_REQUIRED");
        CurrentHousehold current = currentHousehold.requireCurrent();
        return operations.execute(current, "LOSS", key, entryId, new ReasonCommand(reason),
                (entry, now) -> entry.markLost(reason, now));
    }

    @Transactional
    public IdempotentExecutor.Result<InventoryCommandResponse> retire(
            UUID entryId, String key, String reasonValue) {
        String reason = ConsumeInventoryService.reason(reasonValue, "REASON_REQUIRED");
        CurrentHousehold current = currentHousehold.requireCurrent();
        return operations.execute(current, "RETIRE", key, entryId, new ReasonCommand(reason),
                (entry, now) -> entry.retire(reason, now));
    }

    private record ReasonCommand(String reason) {
    }
}
