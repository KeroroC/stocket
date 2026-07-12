package com.stocket.identity.internal.authentication;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.IdentityAuditEvent;
import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.persistence.UserAccountRepository;
import com.stocket.identity.internal.persistence.UserSessionRepository;

@Service
public class AuthenticationService {

    /**
     * Fixed pseudo-digest used for timing-safe checks on unknown usernames.
     * Prevents attackers from detecting whether a username exists via response time.
     */
    private static final String DUMMY_PASSWORD_HASH = "{bcrypt}$2a$10$00000000000000000000000000000000000000000000000000000000";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthenticationService.class);

    private final UserAccountRepository accountRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final TokenHasher tokenHasher;
    private final BoundedRateLimiter<LoginThrottleKey> rateLimiter;
    private final ApplicationEventPublisher eventPublisher;

    AuthenticationService(UserAccountRepository accountRepository,
                           UserSessionRepository sessionRepository,
                           PasswordEncoder passwordEncoder,
                           SessionService sessionService,
                           TokenHasher tokenHasher,
                           BoundedRateLimiter<LoginThrottleKey> rateLimiter,
                           ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.tokenHasher = tokenHasher;
        this.rateLimiter = rateLimiter;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LoginResult login(String username, String rawPassword,
                             String userAgent, String sourceAddress, Instant now) {
        String normalizedUsername = normalizeUsername(username);
        String usernameFingerprint = tokenHasher.sha256(normalizedUsername);
        LoginThrottleKey throttleKey = new LoginThrottleKey(normalizedUsername, sourceAddress);

        // Check rate limit before doing any work
        if (!rateLimiter.tryAcquire(throttleKey)) {
            log.warn("Login rate limited for source: {}", sourceAddress);
            return LoginResult.rateLimited();
        }

        // Look up account
        UserAccount account = accountRepository.findByNormalizedUsername(normalizedUsername)
                .orElse(null);

        if (account == null) {
            // Unknown user: run dummy password check for constant-time behavior
            passwordEncoder.matches(rawPassword, DUMMY_PASSWORD_HASH);
            publishAuditEvent("LoginFailed", "FAILURE", null, sourceAddress,
                    Map.of("usernameFingerprint", usernameFingerprint));
            return LoginResult.invalidCredentials();
        }

        // Check if account is disabled
        if (!account.isEnabled()) {
            // Still check password to maintain constant-time behavior
            passwordEncoder.matches(rawPassword, account.getPasswordHash());
            publishAuditEvent("LoginFailed", "FAILURE", null, sourceAddress,
                    Map.of("usernameFingerprint", usernameFingerprint, "reason", "ACCOUNT_DISABLED"));
            return LoginResult.invalidCredentials();
        }

        // Verify password
        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            publishAuditEvent("LoginFailed", "FAILURE", null, sourceAddress,
                    Map.of("usernameFingerprint", usernameFingerprint));
            return LoginResult.invalidCredentials();
        }

        // Success: clear the rate limit bucket for this key
        rateLimiter.reset(throttleKey);

        // Create session
        CreatedSession createdSession = sessionService.create(account, userAgent, sourceAddress, now);

        publishAuditEvent("LoginSucceeded", "SUCCESS", account.getId(), sourceAddress, Map.of());

        return LoginResult.success(account.getId(), account.getUsername(), createdSession);
    }

    @Transactional
    public void logout(String rawToken, Instant now) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        String tokenHash = tokenHasher.sha256(rawToken);

        // Resolve account ID before revoking (session still active at this point)
        UUID actorAccountId = sessionRepository.findActiveByTokenHash(tokenHash, now)
                .map(session -> session.getAccount().getId())
                .orElse(null);

        int revoked = sessionRepository.revokeByTokenHash(tokenHash, now, "USER_LOGOUT");
        if (revoked > 0) {
            publishAuditEvent("LoggedOut", "SUCCESS", actorAccountId, null, Map.of());
        }
    }

    private void publishAuditEvent(String eventType, String outcome,
                                   UUID actorAccountId, String source,
                                   Map<String, Object> details) {
        IdentityAuditEvent event = new IdentityAuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                eventType,
                outcome,
                actorAccountId,
                "USER_ACCOUNT",
                actorAccountId,
                null,
                source != null ? source : "api",
                details);
        eventPublisher.publishEvent(event);
    }

    private static String normalizeUsername(String username) {
        return username.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Result of a login attempt.
     */
    public sealed interface LoginResult {
        record Success(UUID accountId, String username, CreatedSession session) implements LoginResult {
        }
        record Failure(String code) implements LoginResult {
        }

        static LoginResult success(UUID accountId, String username, CreatedSession session) {
            return new Success(accountId, username, session);
        }

        static LoginResult invalidCredentials() {
            return new Failure("INVALID_CREDENTIALS");
        }

        static LoginResult rateLimited() {
            return new Failure("RATE_LIMITED");
        }
    }
}
