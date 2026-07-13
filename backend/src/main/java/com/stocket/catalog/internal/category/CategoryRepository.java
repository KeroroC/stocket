package com.stocket.catalog.internal.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByHouseholdIdAndId(UUID householdId, UUID id);

    List<Category> findByHouseholdId(UUID householdId);

    boolean existsByHouseholdIdAndParentAndArchivedAtIsNull(UUID householdId, Category parent);

    boolean existsByHouseholdIdAndParentAndNormalizedNameAndArchivedAtIsNull(
            UUID householdId, Category parent, String normalizedName);

    boolean existsByHouseholdIdAndParentAndNormalizedNameAndArchivedAtIsNullAndIdNot(
            UUID householdId, Category parent, String normalizedName, UUID id);
}
