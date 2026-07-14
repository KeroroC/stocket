package com.stocket.catalog;

import java.util.List;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record CatalogExportRow(UUID id, String name, String categoryPath, String brand, String model,
                               String specification, List<String> tags, List<String> barcodes) { }
