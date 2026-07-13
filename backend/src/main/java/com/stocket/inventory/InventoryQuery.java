package com.stocket.inventory;

import java.util.Optional;
import java.util.UUID;

public interface InventoryQuery {

    Optional<InventoryItemAvailability> availability(UUID householdId, UUID itemId);
}
