package com.stocket.catalog.internal.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;

record ItemResponse(UUID id, String name, UUID categoryId, String brand, String model, String specification,
                    String defaultUnit, Integer defaultShelfLifeValue, ShelfLifeUnit defaultShelfLifeUnit,
                    Map<String, Object> customAttributes, List<String> barcodes, List<String> tags,
                    long version, boolean archived) {
}
