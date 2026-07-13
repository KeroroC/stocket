package com.stocket.inventory.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import com.stocket.inventory.internal.domain.InventoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryEntryRepository extends JpaRepository<InventoryEntry, UUID> {

    Optional<InventoryEntry> findByHouseholdIdAndId(UUID householdId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from InventoryEntry entry where entry.householdId = :householdId and entry.id = :id")
    Optional<InventoryEntry> findByHouseholdIdAndIdForUpdate(
            @Param("householdId") UUID householdId, @Param("id") UUID id);
}
