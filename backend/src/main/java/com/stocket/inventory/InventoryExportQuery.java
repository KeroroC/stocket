package com.stocket.inventory;

import java.util.List;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public interface InventoryExportQuery {
    long countForExport(UUID householdId, InventoryFilter filter);
    List<InventoryExportRow> exportPage(UUID householdId, InventoryFilter filter, UUID afterId, int size);
}
