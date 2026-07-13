package com.stocket.location.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findByHouseholdIdAndId(UUID householdId, UUID id);
    Optional<Location> findByHouseholdIdAndPublicCode(UUID householdId, String publicCode);
    List<Location> findByHouseholdId(UUID householdId);
    boolean existsByHouseholdIdAndParentAndNormalizedNameAndArchivedAtIsNull(
            UUID householdId, Location parent, String normalizedName);
    boolean existsByHouseholdIdAndParentAndNormalizedNameAndArchivedAtIsNullAndIdNot(
            UUID householdId, Location parent, String normalizedName, UUID id);
}
