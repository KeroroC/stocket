package com.stocket.inventory.internal.persistence;

import java.util.UUID;

import com.stocket.inventory.internal.domain.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
}
