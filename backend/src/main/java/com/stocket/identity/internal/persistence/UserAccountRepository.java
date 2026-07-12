package com.stocket.identity.internal.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stocket.identity.internal.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByNormalizedUsername(String normalizedUsername);

    @Query("select a from UserAccount a where a.normalizedUsername = :normalizedUsername")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAccount> findByNormalizedUsernameWithLock(@Param("normalizedUsername") String normalizedUsername);
}
