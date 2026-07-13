package com.stocket.catalog.internal.item;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ItemRepository extends JpaRepository<ItemDefinition, UUID> {
    Optional<ItemDefinition> findByHouseholdIdAndId(UUID householdId, UUID id);
    boolean existsByHouseholdIdAndCategoryIdAndArchivedAtIsNull(UUID householdId, UUID categoryId);
}
