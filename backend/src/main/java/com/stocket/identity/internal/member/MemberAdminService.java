package com.stocket.identity.internal.member;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.stocket.identity.IdentityAuditEvent;
import com.stocket.identity.IdentityRole;
import com.stocket.identity.internal.authentication.BoundedRateLimiter;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.domain.Household;
import com.stocket.identity.internal.domain.HouseholdMember;
import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;
import com.stocket.identity.internal.persistence.HouseholdRepository;
import com.stocket.identity.internal.persistence.UserAccountRepository;
import com.stocket.identity.internal.persistence.UserSessionRepository;
import com.stocket.identity.internal.web.MemberResponse;

@Service
public class MemberAdminService {

    private final UserAccountRepository accountRepository;
    private final HouseholdMemberRepository memberRepository;
    private final HouseholdRepository householdRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureValueGenerator secureValueGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final BoundedRateLimiter<ResetPasswordKey> resetPasswordRateLimiter;

    MemberAdminService(UserAccountRepository accountRepository,
                       HouseholdMemberRepository memberRepository,
                       HouseholdRepository householdRepository,
                       UserSessionRepository sessionRepository,
                       PasswordEncoder passwordEncoder,
                       SecureValueGenerator secureValueGenerator,
                       ApplicationEventPublisher eventPublisher,
                       Clock clock) {
        this.accountRepository = accountRepository;
        this.memberRepository = memberRepository;
        this.householdRepository = householdRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.secureValueGenerator = secureValueGenerator;
        this.eventPublisher = eventPublisher;
        // 5 resets per 15 minutes per actor-target pair, max 10000 keys
        this.resetPasswordRateLimiter = new BoundedRateLimiter<>(
                clock, Duration.ofMinutes(15), 5, 10000);
    }

    public BoundedRateLimiter<ResetPasswordKey> getResetPasswordRateLimiter() {
        return resetPasswordRateLimiter;
    }

    /**
     * Creates a new member in the household with a temporary password.
     * The temporary password is returned in plaintext only in the response.
     */
    @Transactional
    public MemberResponse createMember(UUID householdId, String username,
                                        String displayName, IdentityRole role, Instant now) {
        String normalizedUsername = username.toLowerCase(Locale.ROOT).trim();

        // Check username uniqueness
        if (accountRepository.findByNormalizedUsername(normalizedUsername).isPresent()) {
            throw new DuplicateUsernameException();
        }

        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new IllegalArgumentException("Household not found"));

        // Generate temporary password
        String temporaryPassword = secureValueGenerator.generateTemporaryPassword();
        String passwordHash = passwordEncoder.encode(temporaryPassword);

        // Create account
        UserAccount account = new UserAccount(
                UUID.randomUUID(),
                username.trim(),
                normalizedUsername,
                displayName != null ? displayName : username.trim(),
                passwordHash,
                now);
        account.setMustChangePassword(true);
        accountRepository.save(account);

        // Create household member
        HouseholdMember member = new HouseholdMember(
                UUID.randomUUID(), household, account, role, now);
        memberRepository.save(member);

        publishAuditEvent("MemberCreated", "SUCCESS", null, Map.of(
                "targetAccountId", account.getId().toString(),
                "role", role.name()));

        return new MemberResponse(
                member.getId(),
                account.getUsername(),
                account.getDisplayName(),
                member.getRole(),
                temporaryPassword);
    }

    /**
     * Returns all members of the household.
     */
    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(UUID householdId) {
        return memberRepository.findByHouseholdId(householdId).stream()
                .map(m -> new MemberResponse(
                        m.getId(),
                        m.getAccount().getUsername(),
                        m.getAccount().getDisplayName(),
                        m.getRole(),
                        null))
                .toList();
    }

    /**
     * Returns a single member by ID, scoped to the requesting admin's household.
     */
    @Transactional(readOnly = true)
    public MemberResponse getMember(UUID householdId, UUID memberId) {
        HouseholdMember member = memberRepository.findByHouseholdIdAndId(householdId, memberId)
                .orElseThrow(() -> new MemberNotFoundException());

        return new MemberResponse(
                member.getId(),
                member.getAccount().getUsername(),
                member.getAccount().getDisplayName(),
                member.getRole(),
                null);
    }

    /**
     * Updates a member's role with last-admin protection.
     * Uses pessimistic locking to ensure atomicity of the last-admin check.
     * Scoped to the requesting admin's household.
     */
    @Transactional
    public MemberResponse updateRole(UUID householdId, UUID memberId, IdentityRole newRole, Instant now) {
        HouseholdMember member = memberRepository.findByHouseholdIdAndId(householdId, memberId)
                .orElseThrow(() -> new MemberNotFoundException());

        IdentityRole oldRole = member.getRole();

        // If demoting an admin, check last-admin protection
        if (oldRole == IdentityRole.ADMIN && newRole != IdentityRole.ADMIN) {
            assertNotLastAdmin(member.getHousehold().getId());
        }

        member.setRole(newRole);
        member.setUpdatedAt(now);
        memberRepository.save(member);

        publishAuditEvent("MemberRoleChanged", "SUCCESS", null, Map.of(
                "targetMemberId", memberId.toString(),
                "oldRole", oldRole.name(),
                "newRole", newRole.name()));

        return new MemberResponse(
                member.getId(),
                member.getAccount().getUsername(),
                member.getAccount().getDisplayName(),
                member.getRole(),
                null);
    }

    /**
     * Disables a member's account with last-admin protection.
     * Scoped to the requesting admin's household.
     */
    @Transactional
    public void disableMember(UUID householdId, UUID memberId, Instant now) {
        HouseholdMember member = memberRepository.findByHouseholdIdAndId(householdId, memberId)
                .orElseThrow(() -> new MemberNotFoundException());

        // If disabling an admin, check last-admin protection
        if (member.getRole() == IdentityRole.ADMIN) {
            assertNotLastAdmin(member.getHousehold().getId());
        }

        member.getAccount().disable(now);
        accountRepository.save(member.getAccount());

        publishAuditEvent("MemberStatusChanged", "SUCCESS", null, Map.of(
                "targetMemberId", memberId.toString()));
    }

    /**
     * Resets a member's password with rate limiting by actor+target pair.
     * Revokes all existing sessions and returns a new temporary password.
     * Scoped to the requesting admin's household.
     */
    @Transactional
    public String resetPassword(UUID householdId, UUID actorId, UUID memberId, Instant now) {
        HouseholdMember member = memberRepository.findByHouseholdIdAndId(householdId, memberId)
                .orElseThrow(() -> new MemberNotFoundException());

        // Rate limit by actor+target pair
        ResetPasswordKey rateLimitKey = new ResetPasswordKey(actorId, memberId);
        if (!resetPasswordRateLimiter.tryAcquire(rateLimitKey)) {
            throw new ResetPasswordRateLimitedException();
        }

        UserAccount account = member.getAccount();

        // Generate new temporary password
        String temporaryPassword = secureValueGenerator.generateTemporaryPassword();
        String newPasswordHash = passwordEncoder.encode(temporaryPassword);

        // Update account
        account.setPasswordHash(newPasswordHash);
        account.setMustChangePassword(true);
        account.setCredentialsChangedAt(now);
        account.setUpdatedAt(now);
        accountRepository.save(account);

        // Revoke all sessions for this account
        sessionRepository.revokeAllByAccountId(account.getId(), now, "PASSWORD_RESET_BY_ADMIN");

        publishAuditEvent("PasswordResetByAdmin", "SUCCESS", actorId, Map.of(
                "targetAccountId", account.getId().toString()));

        return temporaryPassword;
    }

    /**
     * Asserts that the household has more than one active admin.
     * Uses pessimistic locking to prevent concurrent last-admin demotion.
     */
    private void assertNotLastAdmin(UUID householdId) {
        // Lock all active admin rows for this household
        List<HouseholdMember> activeAdmins =
                memberRepository.findActiveMembersByHouseholdIdAndRole(householdId, IdentityRole.ADMIN);

        if (activeAdmins.size() <= 1) {
            throw new LastAdminRequiredException();
        }
    }

    private void publishAuditEvent(String eventType, String outcome,
                                   UUID actorAccountId, Map<String, Object> details) {
        IdentityAuditEvent event = new IdentityAuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                eventType,
                outcome,
                actorAccountId,
                "HOUSEHOLD_MEMBER",
                null,
                null,
                "api",
                details);
        eventPublisher.publishEvent(event);
    }

    public record ResetPasswordKey(UUID actorId, UUID targetId) {
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class LastAdminRequiredException extends RuntimeException {
        public LastAdminRequiredException() {
            super("Cannot disable or demote the last active admin");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException() {
            super("Username already exists");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class MemberNotFoundException extends RuntimeException {
        public MemberNotFoundException() {
            super("Member not found");
        }
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public static class ResetPasswordRateLimitedException extends RuntimeException {
        public ResetPasswordRateLimitedException() {
            super("Too many password reset attempts");
        }
    }
}
