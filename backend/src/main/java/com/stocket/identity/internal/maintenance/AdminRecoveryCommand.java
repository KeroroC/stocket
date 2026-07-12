package com.stocket.identity.internal.maintenance;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.IdentityAuditEvent;
import com.stocket.identity.IdentityRole;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.domain.HouseholdMember;
import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;
import com.stocket.identity.internal.persistence.UserAccountRepository;
import com.stocket.identity.internal.persistence.UserSessionRepository;

/**
 * Local administrator recovery command.
 * Resets an admin's password and revokes all sessions when the system
 * is started with the {@code --stocket.maintenance.reset-admin} parameter.
 *
 * <p>This command is intended for disaster recovery scenarios where the
 * administrator has lost access to their account and cannot use the normal
 * password reset flow.
 */
@Component
public class AdminRecoveryCommand {

    private static final Logger log = LoggerFactory.getLogger(AdminRecoveryCommand.class);

    private final UserAccountRepository accountRepository;
    private final HouseholdMemberRepository memberRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureValueGenerator secureValueGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    AdminRecoveryCommand(UserAccountRepository accountRepository,
                         HouseholdMemberRepository memberRepository,
                         UserSessionRepository sessionRepository,
                         PasswordEncoder passwordEncoder,
                         SecureValueGenerator secureValueGenerator,
                         ApplicationEventPublisher eventPublisher,
                         Clock clock) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.secureValueGenerator = secureValueGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Resets the password for the specified admin user.
     *
     * <p>This method:
     * <ul>
     *   <li>Finds the user by normalized username</li>
     *   <li>Verifies the user has the ADMIN role</li>
     *   <li>Generates a new temporary password</li>
     *   <li>Updates the password hash and sets must_change_password flag</li>
     *   <li>Revokes all existing sessions</li>
     *   <li>Publishes a LOCAL_MAINTENANCE audit event</li>
     *   <li>Returns the temporary password (caller should print after AFTER_COMMIT)</li>
     * </ul>
     *
     * @param username the username of the admin to recover
     * @return the generated temporary password
     * @throws AdminNotFoundException if the user does not exist
     * @throws NotAdminException if the user does not have the ADMIN role
     */
    @Transactional
    public String resetAdmin(String username) {
        String normalizedUsername = username.toLowerCase(Locale.ROOT).trim();
        Instant now = clock.instant();

        log.info("Starting admin recovery for user: {}", normalizedUsername);

        // Find account
        UserAccount account = accountRepository.findByNormalizedUsername(normalizedUsername)
                .orElseThrow(() -> {
                    log.error("User not found: {}", normalizedUsername);
                    return new AdminNotFoundException("User not found: " + normalizedUsername);
                });

        // Verify ADMIN role
        HouseholdMember member = memberRepository.findByAccountId(account.getId())
                .orElseThrow(() -> {
                    log.error("User {} has no household membership", normalizedUsername);
                    return new NotAdminException("User has no household membership: " + normalizedUsername);
                });

        if (member.getRole() != IdentityRole.ADMIN) {
            log.error("User {} does not have ADMIN role (has: {})", normalizedUsername, member.getRole());
            throw new NotAdminException("User does not have ADMIN role: " + normalizedUsername);
        }

        // Generate temporary password
        String temporaryPassword = secureValueGenerator.generateTemporaryPassword();
        String newPasswordHash = passwordEncoder.encode(temporaryPassword);

        // Update account - use direct setter + saveAndFlush to ensure changes are persisted
        account.setPasswordHash(newPasswordHash);
        account.setMustChangePassword(true);
        account.setCredentialsChangedAt(now);
        account.setUpdatedAt(now);
        accountRepository.saveAndFlush(account);

        log.info("Password updated for user: {}", normalizedUsername);

        // Revoke all sessions
        int revokedCount = sessionRepository.revokeAllByAccountId(
                account.getId(), now, "LOCAL_MAINTENANCE_RECOVERY");
        log.info("Revoked {} sessions for user: {}", revokedCount, normalizedUsername);

        // Publish audit event
        IdentityAuditEvent auditEvent = new IdentityAuditEvent(
                UUID.randomUUID(),
                now,
                "LOCAL_MAINTENANCE",
                "SUCCESS",
                account.getId(),
                "USER_ACCOUNT",
                account.getId(),
                null,
                "maintenance",
                Map.of("action", "password_recovery",
                        "username", normalizedUsername));
        eventPublisher.publishEvent(auditEvent);

        log.info("Admin recovery completed for user: {}", normalizedUsername);

        return temporaryPassword;
    }

    /**
     * Exception thrown when the specified user does not exist.
     */
    public static class AdminNotFoundException extends RuntimeException {
        public AdminNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when the specified user does not have the ADMIN role.
     */
    public static class NotAdminException extends RuntimeException {
        public NotAdminException(String message) {
            super(message);
        }
    }
}
