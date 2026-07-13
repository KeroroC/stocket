package com.stocket.inventory.internal.web;

import java.util.UUID;

public record TransferRequest(UUID targetLocationId, String quantity) {
}
