package com.stocket.catalog;

import java.util.List;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public interface CatalogExportQuery {
    long countForExport(UUID householdId, CatalogFilter filter);
    List<CatalogExportRow> exportPage(UUID householdId, CatalogFilter filter, UUID afterId, int size);
}
