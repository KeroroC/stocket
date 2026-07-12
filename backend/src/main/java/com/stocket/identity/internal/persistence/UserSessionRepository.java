package com.stocket.identity.internal.persistence;

import java.time.Instant;
import java.util.List;
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

    @Query("select s from UserSession s where s.account.id = :accountId " +
           "and s.revokedAt is null " +
           "and s.absoluteExpiresAt > :now " +
           "and s.idleExpiresAt > :now " +
           "order by s.createdAt desc")
    List<UserSession> findActiveByAccountId(@Param("accountId") UUID accountId,
                                             @Param("now") Instant now);

    Optional<UserSession> findByIdAndAccountId(UUID id, UUID accountId);

    @Modifying(clearAutomatically = true)
    @Query("update UserSession s set s.revokedAt = :now, s.revokeReason = :reason " +
           "where s.tokenHash = :tokenHash and s.revokedAt is null")
    int revokeByTokenHash(@Param("tokenHash") String tokenHash,
                          @Param("now") Instant now,
                          @Param("reason") String reason);

    @Modifying(clearAutomatically = true)
    @Query("update UserSession s set s.revokedAt = :now, s.revokeReason = :reason " +
           "where s.account.id = :accountId and s.revokedAt is null")
    int revokeAllByAccountId(@Param("accountId") UUID accountId,
                              @Param("now") Instant now,
                              @Param("reason") String reason);

    @Modifying(clearAutomatically = true)
    @Query("update UserSession s set s.revokedAt = :now, s.revokeReason = :reason " +
           "where s.account.id = :accountId and s.id <> :exceptSessionId and s.revokedAt is null")
    int revokeAllByAccountIdExcept(@Param("accountId") UUID accountId,
                                    @Param("exceptSessionId") UUID exceptSessionId,
                                    @Param("now") Instant now,
                                    @Param("reason") String reason);
}
