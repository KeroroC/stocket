package com.stocket.identity.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import com.stocket.identity.internal.domain.MemberInvite;

public interface MemberInviteRepository extends JpaRepository<MemberInvite, UUID> {

    /**
     * Finds an invite by token hash with pessimistic write lock.
     * Used during acceptance to prevent concurrent acceptance.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0")})
    Optional<MemberInvite> findLockedByTokenHash(String tokenHash);

    /**
     * Finds an invite by token hash without locking.
     * Used for read-only status checks.
     */
    Optional<MemberInvite> findByTokenHash(String tokenHash);

    List<MemberInvite> findByHouseholdIdOrderByCreatedAtDesc(UUID householdId);

    Optional<MemberInvite> findByHouseholdIdAndId(UUID householdId, UUID id);
}
