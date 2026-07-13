package com.stocket.identity.internal.setup;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.IdentityRole;
import com.stocket.identity.internal.authentication.PasswordPolicy;
import com.stocket.identity.internal.domain.Household;
import com.stocket.identity.internal.domain.HouseholdMember;
import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;
import com.stocket.identity.internal.persistence.HouseholdRepository;
import com.stocket.identity.internal.persistence.UserAccountRepository;

@Component
public class SetupTransaction {

    private final HouseholdRepository householdRepository;
    private final UserAccountRepository userAccountRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EntityManager entityManager;

    SetupTransaction(HouseholdRepository householdRepository,
                     UserAccountRepository userAccountRepository,
                     HouseholdMemberRepository householdMemberRepository,
                     PasswordEncoder passwordEncoder,
                     PasswordPolicy passwordPolicy,
                     EntityManager entityManager) {
        this.householdRepository = householdRepository;
        this.userAccountRepository = userAccountRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.entityManager = entityManager;
    }

    @Transactional
    public InitializeResult create(InitializeCommand command) {
        Instant now = Instant.now();
        String normalizedUsername = command.username().toLowerCase(Locale.ROOT).trim();

        // Validate password
        var errors = passwordPolicy.validate(normalizedUsername, command.password());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid password: " + String.join(", ", errors));
        }

        // Create household
        Household household = new Household(UUID.randomUUID(), command.householdName(), command.timezone(), now);
        householdRepository.save(household);

        // Create user account
        String passwordHash = passwordEncoder.encode(command.password());
        UserAccount account = new UserAccount(
                UUID.randomUUID(), command.username().trim(), normalizedUsername,
                command.displayName(), passwordHash, now);
        userAccountRepository.save(account);

        // Create household member
        HouseholdMember member = new HouseholdMember(
                UUID.randomUUID(), household, account, IdentityRole.ADMIN, now);
        householdMemberRepository.save(member);

        // Flush to ensure unique constraints are checked before returning
        entityManager.flush();

        return new InitializeResult(account.getId(), account.getUsername(), member.getRole());
    }
}
