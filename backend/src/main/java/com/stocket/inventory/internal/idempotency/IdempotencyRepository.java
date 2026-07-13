package com.stocket.inventory.internal.idempotency;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByAccountIdAndOperationAndIdempotencyKey(
            UUID accountId, String operation, String idempotencyKey);
}
