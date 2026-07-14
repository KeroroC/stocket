package com.stocket.inventory;

import java.util.Optional;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public interface InventoryQuery {

    Optional<InventoryItemAvailability> availability(UUID householdId, UUID itemId);
    boolean existsEntry(UUID householdId, UUID entryId);
}
