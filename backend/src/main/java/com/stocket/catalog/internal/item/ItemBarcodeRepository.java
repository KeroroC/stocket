package com.stocket.catalog.internal.item;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ItemBarcodeRepository extends JpaRepository<ItemBarcode, UUID> {
    boolean existsByHouseholdIdAndNormalizedValueAndItemDefinitionIdNot(
            UUID householdId, String normalizedValue, UUID itemDefinitionId);
}
