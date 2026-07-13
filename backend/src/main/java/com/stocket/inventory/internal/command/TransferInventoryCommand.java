package com.stocket.inventory.internal.command;

import java.util.UUID;

public record TransferInventoryCommand(UUID sourceEntryId, UUID targetLocationId, String quantity) {
}
