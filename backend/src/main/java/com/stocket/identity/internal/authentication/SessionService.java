package com.stocket.identity.internal.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.IdentityRole;
import com.stocket.identity.internal.config.IdentityProperties;
import com.stocket.identity.internal.domain.HouseholdMember;
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

    SessionService(UserSessionRepository sessionRepository,
                   HouseholdMemberRepository householdMemberRepository,
                   SecureValueGenerator secureValueGenerator,
                   TokenHasher tokenHasher,
                   IdentityProperties properties) {
        this.sessionRepository = sessionRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.secureValueGenerator = secureValueGenerator;
        this.tokenHasher = tokenHasher;
        this.idleTimeout = properties.session().idleTimeout();
        this.absoluteTimeout = properties.session().absoluteTimeout();
        this.touchInterval = properties.session().touchInterval();
    }

    public CreatedSession create(UserAccount account, String userAgent, String sourceAddress, Instant now) {
        String token = secureValueGenerator.generateToken();
        String tokenHash = tokenHasher.sha256(token);
        Instant idleExpiresAt = now.plus(idleTimeout);
        Instant absoluteExpiresAt = now.plus(absoluteTimeout);

        UserSession session = new UserSession(
                UUID.randomUUID(), account, tokenHash,
                now, idleExpiresAt, absoluteExpiresAt,
                userAgent, sourceAddress);

        sessionRepository.save(session);
        return new CreatedSession(session.getId(), token);
    }

    @Transactional
    public Optional<IdentityPrincipal> authenticate(String rawToken, Instant now) {
        String tokenHash = tokenHasher.sha256(rawToken);

        return sessionRepository.findActiveByTokenHash(tokenHash, now)
                .filter(session -> session.getAccount().isEnabled())
                .map(session -> {
                    // Touch session only if enough time has passed
                    Instant nextTouchAt = session.getLastSeenAt().plus(touchInterval);
                    if (!nextTouchAt.isAfter(now)) {
                        Instant newIdleExpiresAt = min(
                                now.plus(idleTimeout),
                                session.getAbsoluteExpiresAt());
                        session.touch(now, newIdleExpiresAt);
                        sessionRepository.save(session);
                    }

                    UserAccount account = session.getAccount();
                    IdentityRole role = householdMemberRepository
                            .findByAccountId(account.getId())
                            .map(HouseholdMember::getRole)
                            .orElse(IdentityRole.VIEWER);

                    return new IdentityPrincipal(
                            account.getId(),
                            account.getUsername(),
                            role,
                            account.isMustChangePassword(),
                            session.getId());
                });
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }
}
