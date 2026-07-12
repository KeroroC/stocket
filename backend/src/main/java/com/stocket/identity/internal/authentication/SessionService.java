package com.stocket.identity.internal.authentication;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.stocket.identity.internal.config.IdentityProperties;
import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.domain.UserSession;
import com.stocket.identity.internal.persistence.UserSessionRepository;

@Service
public class SessionService {

    private final UserSessionRepository sessionRepository;
    private final SecureValueGenerator secureValueGenerator;
    private final TokenHasher tokenHasher;
    private final Duration idleTimeout;
    private final Duration absoluteTimeout;

    SessionService(UserSessionRepository sessionRepository,
                   SecureValueGenerator secureValueGenerator,
                   TokenHasher tokenHasher,
                   IdentityProperties properties) {
        this.sessionRepository = sessionRepository;
        this.secureValueGenerator = secureValueGenerator;
        this.tokenHasher = tokenHasher;
        this.idleTimeout = properties.session().idleTimeout();
        this.absoluteTimeout = properties.session().absoluteTimeout();
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
}
