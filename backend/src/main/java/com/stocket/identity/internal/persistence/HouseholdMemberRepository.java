package com.stocket.identity.internal.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stocket.identity.IdentityRole;
import com.stocket.identity.internal.domain.HouseholdMember;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, UUID> {

    Optional<HouseholdMember> findByAccountId(UUID accountId);

    List<HouseholdMember> findByHouseholdId(UUID householdId);

    @Query("select m from HouseholdMember m join m.account a " +
           "where m.household.id = :householdId and m.role = :role and a.status = 'ACTIVE'")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<HouseholdMember> findActiveMembersByHouseholdIdAndRole(
            @Param("householdId") UUID householdId,
            @Param("role") IdentityRole role);

    @Query("select count(m) from HouseholdMember m join m.account a " +
           "where m.household.id = :householdId and m.role = 'ADMIN' and a.status = 'ACTIVE'")
    int countActiveAdminsByHouseholdId(@Param("householdId") UUID householdId);
}
