package com.stocket.catalog.internal.item;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record ItemRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UUID categoryId,
        @Size(max = 120) String brand,
        @Size(max = 120) String model,
        @Size(max = 255) String specification,
        @NotBlank @Size(max = 32) String defaultUnit,
        @Positive Integer defaultShelfLifeValue,
        ShelfLifeUnit defaultShelfLifeUnit,
        Map<String, Object> customAttributes,
        List<String> barcodes,
        List<String> tags,
        Long version
) {
    ItemRequest {
        customAttributes = customAttributes == null ? Map.of() : Map.copyOf(customAttributes);
        barcodes = barcodes == null ? List.of() : List.copyOf(barcodes);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
