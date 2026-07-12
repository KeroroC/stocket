package com.stocket.identity.internal.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stocket.identity.internal.domain.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Query("select s from UserSession s where s.tokenHash = :tokenHash " +
           "and s.revokedAt is null " +
           "and s.absoluteExpiresAt > :now " +
           "and s.idleExpiresAt > :now")
    Optional<UserSession> findActiveByTokenHash(@Param("tokenHash") String tokenHash,
                                                 @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("update UserSession s set s.revokedAt = :now, s.revokeReason = :reason " +
           "where s.tokenHash = :tokenHash and s.revokedAt is null")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash,
                          @Param("now") Instant now,
                          @Param("reason") String reason);
}
