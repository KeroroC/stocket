package com.stocket.inventory.internal.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import com.stocket.inventory.internal.domain.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    @Query(value = """
            select coalesce(-sum(quantity_delta), 0)
            from inventory_movement
            where entry_id = :entryId and movement_type in ('CONSUME', 'RETURN')
            """, nativeQuery = true)
    BigDecimal outstandingConsumed(@Param("entryId") UUID entryId);
}
