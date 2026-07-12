package com.stocket.identity.internal.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.IdentityAuditEvent;
import com.stocket.identity.IdentityRole;
import com.stocket.identity.internal.authentication.CreatedSession;
import com.stocket.identity.internal.authentication.PasswordPolicy;
import com.stocket.identity.internal.authentication.SessionService;
import com.stocket.identity.internal.domain.HouseholdMember;
import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.domain.UserSession;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;
import com.stocket.identity.internal.persistence.UserAccountRepository;
import com.stocket.identity.internal.persistence.UserSessionRepository;
import com.stocket.identity.internal.web.AccountResponse;
import com.stocket.identity.internal.web.SessionResponse;

@Service
public class AccountService {

    private final UserAccountRepository accountRepository;
    private final UserSessionRepository sessionRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;

    AccountService(UserAccountRepository accountRepository,
                   UserSessionRepository sessionRepository,
                   HouseholdMemberRepository householdMemberRepository,
                   PasswordEncoder passwordEncoder,
                   PasswordPolicy passwordPolicy,
                   SessionService sessionService,
                   ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.sessionRepository = sessionRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.eventPublisher = eventPublisher;
    }

    public AccountResponse getAccount(UUID accountId) {
        UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        IdentityRole role = householdMemberRepository.findByAccountId(accountId)
                .map(HouseholdMember::getRole)
                .orElse(IdentityRole.VIEWER);

        return new AccountResponse(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getEmail(),
                role,
                account.isMustChangePassword());
    }

    @Transactional
    public AccountResponse updateProfile(UUID accountId, String displayName, String email) {
        UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        account.setDisplayName(displayName);
        account.setEmail(email);
        accountRepository.save(account);

        IdentityRole role = householdMemberRepository.findByAccountId(accountId)
                .map(HouseholdMember::getRole)
                .orElse(IdentityRole.VIEWER);

        return new AccountResponse(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getEmail(),
                role,
                account.isMustChangePassword());
    }

    @Transactional
    public CreatedSession changePassword(UUID accountId, String oldPassword, String newPassword,
                                          String userAgent, String sourceAddress, Instant now) {
        UserAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        // Verify old password
        if (!passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            throw new InvalidOldPasswordException();
        }

        // Validate new password against policy
        List<String> violations = passwordPolicy.validate(account.getNormalizedUsername(), newPassword);
        if (!violations.isEmpty()) {
            throw new PasswordPolicyViolationException(violations);
        }

        // Update password
        String newPasswordHash = passwordEncoder.encode(newPassword);
        account.changePassword(newPasswordHash, now);
        accountRepository.save(account);

        // Revoke all sessions for this account
        sessionRepository.revokeAllByAccountId(accountId, now, "PASSWORD_CHANGED");

        // Create new session
        CreatedSession newSession = sessionService.create(account, userAgent, sourceAddress, now);

        // Publish event
        publishAuditEvent("PASSWORD_CHANGED", "SUCCESS", accountId, sourceAddress);

        return newSession;
    }

    public List<SessionResponse> listSessions(UUID accountId, UUID currentSessionId, Instant now) {
        List<UserSession> sessions = sessionRepository.findActiveByAccountId(accountId, now);

        return sessions.stream()
                .map(session -> new SessionResponse(
                        session.getId(),
                        session.getId().equals(currentSessionId),
                        session.getCreatedAt(),
                        session.getLastSeenAt(),
                        session.getAbsoluteExpiresAt(),
                        session.getUserAgent(),
                        session.getSourceAddress()))
                .toList();
    }

    @Transactional
    public boolean revokeSession(UUID accountId, UUID sessionId, Instant now) {
        return sessionRepository.findByIdAndAccountId(sessionId, accountId)
                .map(session -> {
                    session.revoke("USER_REVOKED", now);
                    sessionRepository.save(session);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public void revokeOtherSessions(UUID accountId, UUID currentSessionId, Instant now) {
        sessionRepository.revokeAllByAccountIdExcept(accountId, currentSessionId, now, "USER_REVOKED_OTHERS");
    }

    private void publishAuditEvent(String eventType, String outcome,
                                   UUID actorAccountId, String source) {
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
                java.util.Map.of());
        eventPublisher.publishEvent(event);
    }

    public static class InvalidOldPasswordException extends RuntimeException {
        public InvalidOldPasswordException() {
            super("Old password is incorrect");
        }
    }

    public static class PasswordPolicyViolationException extends RuntimeException {
        private final List<String> violations;

        public PasswordPolicyViolationException(List<String> violations) {
            super("Password policy violation: " + String.join(", ", violations));
            this.violations = List.copyOf(violations);
        }

        public List<String> getViolations() {
            return violations;
        }
    }
}
