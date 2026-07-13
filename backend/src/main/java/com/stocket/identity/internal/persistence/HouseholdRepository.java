package com.stocket.identity.internal.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.stocket.identity.internal.domain.Household;

public interface HouseholdRepository extends JpaRepository<Household, UUID> {

    @Query("select case when count(h) > 0 then true else false end from Household h")
    boolean existsAny();
}
