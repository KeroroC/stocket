package com.stocket.identity.internal.invite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.stocket.identity.IdentityAuditEvent;
import com.stocket.identity.IdentityRole;
import com.stocket.identity.internal.authentication.BoundedRateLimiter;
import com.stocket.identity.internal.authentication.SecureValueGenerator;
import com.stocket.identity.internal.authentication.TokenHasher;
import com.stocket.identity.internal.config.IdentityProperties;
import com.stocket.identity.internal.domain.Household;
import com.stocket.identity.internal.domain.HouseholdMember;
import com.stocket.identity.internal.domain.MemberInvite;
import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;
import com.stocket.identity.internal.persistence.HouseholdRepository;
import com.stocket.identity.internal.persistence.MemberInviteRepository;
import com.stocket.identity.internal.persistence.UserAccountRepository;

@Service
public class InviteService {

    private static final int MAX_EXPIRY_DAYS = 30;
    private static final Duration ACCEPT_RATE_LIMIT_WINDOW = Duration.ofMinutes(15);
    private static final int ACCEPT_RATE_LIMIT_MAX = 5;
    private static final int ACCEPT_RATE_LIMIT_MAX_KEYS = 10000;

    private final MemberInviteRepository inviteRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final UserAccountRepository accountRepository;
    private final SecureValueGenerator secureValueGenerator;
    private final TokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final IdentityProperties properties;
    private final EntityManager entityManager;
    private final Clock clock;
    private final BoundedRateLimiter<String> acceptRateLimiter;

    InviteService(MemberInviteRepository inviteRepository,
                  HouseholdRepository householdRepository,
                  HouseholdMemberRepository householdMemberRepository,
                  UserAccountRepository accountRepository,
                  SecureValueGenerator secureValueGenerator,
                  TokenHasher tokenHasher,
                  PasswordEncoder passwordEncoder,
                  ApplicationEventPublisher eventPublisher,
                  IdentityProperties properties,
                  EntityManager entityManager,
                  Clock clock) {
        this.inviteRepository = inviteRepository;
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.accountRepository = accountRepository;
        this.secureValueGenerator = secureValueGenerator;
        this.tokenHasher = tokenHasher;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.entityManager = entityManager;
        this.clock = clock;
        this.acceptRateLimiter = new BoundedRateLimiter<>(
                clock, ACCEPT_RATE_LIMIT_WINDOW, ACCEPT_RATE_LIMIT_MAX, ACCEPT_RATE_LIMIT_MAX_KEYS);
    }

    public BoundedRateLimiter<String> getAcceptRateLimiter() {
        return acceptRateLimiter;
    }

    /**
     * Creates a new invite for the household.
     * Generates a 32-byte URL-safe token, stores only the SHA-256 hash.
     * Returns the raw token (only available once in the response).
     */
    @Transactional
    public InviteCreationResult createInvite(UUID householdId, IdentityRole role,
                                              Instant customExpiry, Integer maxUses,
                                              UUID createdByAccountId, Instant now) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new IllegalArgumentException("Household not found"));

        UserAccount createdBy = accountRepository.findById(createdByAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        // Determine expiry
        Instant expiresAt;
        if (customExpiry != null) {
            if (customExpiry.isBefore(now)) {
                throw new InvalidExpiryException("Expiry must be in the future");
            }
            if (customExpiry.isAfter(now.plus(MAX_EXPIRY_DAYS, ChronoUnit.DAYS))) {
                throw new InvalidExpiryException("Expiry cannot be more than " + MAX_EXPIRY_DAYS + " days");
            }
            expiresAt = customExpiry;
        } else {
            expiresAt = now.plus(properties.invite().defaultExpiry());
        }

        // Generate token and hash
        String rawToken = secureValueGenerator.generateToken();
        String tokenHash = tokenHasher.sha256(rawToken);

        MemberInvite invite = new MemberInvite(
                UUID.randomUUID(), household, tokenHash,
                role, expiresAt, createdBy, now);
        if (maxUses != null && maxUses > 0) {
            invite.setMaxUses(maxUses);
        }
        inviteRepository.save(invite);

        publishAuditEvent("InviteCreated", "SUCCESS", createdByAccountId, Map.of(
                "inviteId", invite.getId().toString(),
                "role", role.name()));

        return new InviteCreationResult(invite.getId(), rawToken);
    }

    /**
     * Lists all invites for a household. Does not expose token information.
     */
    @Transactional(readOnly = true)
    public List<InviteInfo> listInvites(UUID householdId) {
        return inviteRepository.findByHouseholdIdOrderByCreatedAtDesc(householdId).stream()
                .map(invite -> {
                    // Find members who joined via this invite
                    List<String> acceptedByNames = householdMemberRepository
                            .findByJoinedViaInviteId(invite.getId())
                            .stream()
                            .map(m -> m.getAccount().getDisplayName())
                            .toList();

                    return new InviteInfo(
                            invite.getId(),
                            invite.getRole(),
                            invite.getExpiresAt(),
                            invite.getAcceptedAt(),
                            invite.getRevokedAt(),
                            invite.getCreatedAt(),
                            invite.getUseCount(),
                            invite.getMaxUses(),
                            acceptedByNames);
                })
                .toList();
    }

    /**
     * Revokes an invite. Idempotent - returns success even if already revoked.
     */
    @Transactional
    public boolean revokeInvite(UUID householdId, UUID inviteId, Instant now) {
        MemberInvite invite = inviteRepository.findByHouseholdIdAndId(householdId, inviteId)
                .orElseThrow(() -> new InviteNotFoundException());

        if (invite.getRevokedAt() != null) {
            // Already revoked - idempotent
            return true;
        }

        if (invite.getAcceptedAt() != null) {
            throw new InviteAlreadyAcceptedException();
        }

        invite.setRevokedAt(now);
        inviteRepository.save(invite);

        publishAuditEvent("InviteRevoked", "SUCCESS", null, Map.of(
                "inviteId", invite.getId().toString()));

        return true;
    }

    /**
     * Extends the expiry of an invite. Only valid for non-expired, non-revoked invites.
     */
    @Transactional
    public boolean extendInvite(UUID householdId, UUID inviteId, Instant newExpiry, Instant now) {
        MemberInvite invite = inviteRepository.findByHouseholdIdAndId(householdId, inviteId)
                .orElseThrow(() -> new InviteNotFoundException());

        if (invite.getRevokedAt() != null) {
            throw new InviteAlreadyRevokedException();
        }
        if (invite.getExpiresAt().isBefore(now)) {
            throw new InviteAlreadyExpiredException();
        }
        if (newExpiry.isBefore(now)) {
            throw new InvalidExpiryException("New expiry must be in the future");
        }
        if (newExpiry.isAfter(now.plus(MAX_EXPIRY_DAYS, ChronoUnit.DAYS))) {
            throw new InvalidExpiryException("Expiry cannot be more than " + MAX_EXPIRY_DAYS + " days");
        }

        invite.setExpiresAt(newExpiry);
        inviteRepository.save(invite);

        publishAuditEvent("InviteExtended", "SUCCESS", null, Map.of(
                "inviteId", invite.getId().toString(),
                "newExpiry", newExpiry.toString()));

        return true;
    }

    /**
     * Returns public status of an invite by raw token.
     * Only returns whether it's available, the role, and expiry.
     * Uses a non-locked query since this is read-only.
     */
    @Transactional(readOnly = true)
    public InviteStatusResult getInviteStatus(String rawToken, Instant now) {
        String tokenHash = tokenHasher.sha256(rawToken);

        MemberInvite invite = inviteRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InviteNotFoundException());

        return new InviteStatusResult(
                invite.isAvailable() && invite.getExpiresAt().isAfter(now),
                invite.getRole(),
                invite.getExpiresAt());
    }

    /**
     * Accepts an invite, creating a new account and household member.
     * Rate-limited by inviteHash + sourceAddress.
     * Uses pessimistic locking to prevent concurrent acceptance.
     * Account validation failures do NOT consume the invite.
     */
    @Transactional
    public AcceptanceResult acceptInvite(String rawToken, String username,
                                          String displayName, String password,
                                          String sourceAddress, Instant now) {
        String tokenHash = tokenHasher.sha256(rawToken);
        String rateLimitKey = tokenHash + ":" + (sourceAddress != null ? sourceAddress : "unknown");

        // Rate limit check - counts every attempt
        if (!acceptRateLimiter.tryAcquire(rateLimitKey)) {
            throw new AcceptRateLimitedException();
        }

        // Pessimistic lock on invite
        MemberInvite invite;
        try {
            invite = inviteRepository.findLockedByTokenHash(tokenHash)
                    .orElseThrow(() -> new InviteNotFoundException());
        } catch (CannotAcquireLockException e) {
            // Another thread is processing this invite - treat as not available
            throw new InviteNotAvailableException();
        }

        // Validate invite state
        if (!invite.isAvailable()) {
            throw new InviteNotAvailableException();
        }
        if (invite.getExpiresAt().isBefore(now)) {
            throw new InviteExpiredException();
        }

        // Validate account uniqueness BEFORE creating account
        String normalizedUsername = username.toLowerCase(Locale.ROOT).trim();
        if (accountRepository.findByNormalizedUsername(normalizedUsername).isPresent()) {
            throw new DuplicateUsernameException();
        }

        Household household = invite.getHousehold();

        // Generate password hash
        String passwordHash = passwordEncoder.encode(password);

        // Create account
        UserAccount account = new UserAccount(
                UUID.randomUUID(),
                username.trim(),
                normalizedUsername,
                displayName != null ? displayName.trim() : username.trim(),
                passwordHash,
                now);
        account.setMustChangePassword(false);
        accountRepository.save(account);

        // Flush to ensure the account is persisted before referencing it
        entityManager.flush();

        // Create household member
        HouseholdMember member = new HouseholdMember(
                UUID.randomUUID(), household, account, invite.getRole(), now);
        member.setJoinedViaInviteId(invite.getId());
        householdMemberRepository.save(member);

        // Mark invite as used (increment use count)
        invite.setUseCount(invite.getUseCount() + 1);

        // Only set acceptedAt for single-use invites (backward compatibility)
        if (invite.getMaxUses() == 1) {
            invite.setAcceptedAt(now);
            invite.setAcceptedBy(account);
        }
        inviteRepository.save(invite);

        publishAuditEvent("InviteAccepted", "SUCCESS", account.getId(), Map.of(
                "inviteId", invite.getId().toString(),
                "role", invite.getRole().name()));

        return new AcceptanceResult(account.getId(), member.getId());
    }

    private void publishAuditEvent(String eventType, String outcome,
                                   UUID actorAccountId, Map<String, Object> details) {
        IdentityAuditEvent event = new IdentityAuditEvent(
                UUID.randomUUID(),
                clock.instant(),
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

    // --- Result types ---

    public record InviteCreationResult(UUID inviteId, String rawToken) {
    }

    public record InviteInfo(
            UUID id,
            IdentityRole role,
            Instant expiresAt,
            Instant acceptedAt,
            Instant revokedAt,
            Instant createdAt,
            Integer useCount,
            Integer maxUses,
            List<String> acceptedBy) {
    }

    public record InviteStatusResult(
            boolean available,
            IdentityRole role,
            Instant expiresAt) {
    }

    public record AcceptanceResult(UUID accountId, UUID memberId) {
    }

    // --- Exceptions ---

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class InviteNotFoundException extends RuntimeException {
        public InviteNotFoundException() {
            super("Invite not found");
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class InvalidExpiryException extends RuntimeException {
        public InvalidExpiryException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class InviteAlreadyAcceptedException extends RuntimeException {
        public InviteAlreadyAcceptedException() {
            super("Invite has already been accepted");
        }
    }

    @ResponseStatus(HttpStatus.GONE)
    public static class InviteNotAvailableException extends RuntimeException {
        public InviteNotAvailableException() {
            super("Invite is no longer available");
        }
    }

    @ResponseStatus(HttpStatus.GONE)
    public static class InviteExpiredException extends RuntimeException {
        public InviteExpiredException() {
            super("Invite has expired");
        }
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public static class AcceptRateLimitedException extends RuntimeException {
        public AcceptRateLimitedException() {
            super("Too many acceptance attempts");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException() {
            super("Username already exists");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class InviteAlreadyRevokedException extends RuntimeException {
        public InviteAlreadyRevokedException() {
            super("Invite has already been revoked");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class InviteAlreadyExpiredException extends RuntimeException {
        public InviteAlreadyExpiredException() {
            super("Invite has already expired");
        }
    }
}
