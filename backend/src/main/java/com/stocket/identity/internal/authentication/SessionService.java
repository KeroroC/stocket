package com.stocket.identity.internal.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import com.stocket.identity.internal.config.IdentityProperties;
import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.domain.UserSession;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;
import com.stocket.identity.internal.persistence.UserSessionRepository;
import com.stocket.identity.internal.security.IdentityPrincipal;

@Service
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final SecureValueGenerator secureValueGenerator;
    private final TokenHasher tokenHasher;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;
    private final Duration touchInterval;
    private final EntityManager entityManager;

    SessionService(UserSessionRepository sessionRepository,
                   HouseholdMemberRepository householdMemberRepository,
                   SecureValueGenerator secureValueGenerator,
                   TokenHasher tokenHasher,
                   IdentityProperties properties,
                   EntityManager entityManager) {
        this.sessionRepository = sessionRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.secureValueGenerator = secureValueGenerator;
        this.tokenHasher = tokenHasher;
        this.idleTimeout = properties.session().idleTimeout();
        this.absoluteTimeout = properties.session().absoluteTimeout();
        this.touchInterval = properties.session().touchInterval();
        this.entityManager = entityManager;
    }

    @Transactional
    public CreatedSession create(UserAccount account, String userAgent, String sourceAddress, Instant now) {
        String token = secureValueGenerator.generateToken();
        String tokenHash = tokenHasher.sha256(token);
        Instant idleExpiresAt = now.plus(idleTimeout);
        Instant absoluteExpiresAt = now.plus(absoluteTimeout);

        UserSession session = new UserSession(
                UUID.randomUUID(), account, tokenHash,
                now, idleExpiresAt, absoluteExpiresAt,
                userAgent, sourceAddress);

        entityManager.persist(session);
        return new CreatedSession(session.getId(), token);
    }

    @Transactional
    public Optional<IdentityPrincipal> authenticate(String rawToken, Instant now) {
        String tokenHash = tokenHasher.sha256(rawToken);

        return sessionRepository.findActiveByTokenHash(tokenHash, now)
                .filter(session -> session.getAccount().isEnabled())
                .flatMap(session -> {
                    UserAccount account = session.getAccount();
                    return householdMemberRepository.findByAccountId(account.getId())
                            .map(member -> {
                                // Touch session only if enough time has passed
                                Instant nextTouchAt = session.getLastSeenAt().plus(touchInterval);
                                if (!nextTouchAt.isAfter(now)) {
                                    Instant newIdleExpiresAt = min(
                                            now.plus(idleTimeout),
                                            session.getAbsoluteExpiresAt());
                                    session.touch(now, newIdleExpiresAt);
                                }

                                return new IdentityPrincipal(
                                        account.getId(),
                                        member.getHousehold().getId(),
                                        member.getId(),
                                        account.getUsername(),
                                        member.getRole(),
                                        account.isMustChangePassword(),
                                        session.getId());
                            });
                });
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
