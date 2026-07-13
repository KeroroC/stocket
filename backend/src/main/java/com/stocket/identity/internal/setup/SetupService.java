package com.stocket.identity.internal.setup;

import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.stocket.identity.internal.domain.UserAccount;
import com.stocket.identity.internal.persistence.HouseholdRepository;
import com.stocket.identity.internal.persistence.UserAccountRepository;

@Service
public class SetupService {

    private final HouseholdRepository householdRepository;
    private final UserAccountRepository userAccountRepository;
    private final SetupTransaction setupTransaction;

    SetupService(HouseholdRepository householdRepository,
                 UserAccountRepository userAccountRepository,
                 SetupTransaction setupTransaction) {
        this.householdRepository = householdRepository;
        this.userAccountRepository = userAccountRepository;
        this.setupTransaction = setupTransaction;
    }

    public boolean isSetupCompleted() {
        return householdRepository.existsAny();
    }

    public UserAccount findAccountById(UUID accountId) {
        return userAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    public InitializeResult initialize(InitializeCommand command) {
        // Normalize username for duplicate check
        String normalizedUsername = command.username().toLowerCase(Locale.ROOT).trim();

        // Check if username already exists (optimistic check before transaction)
        if (userAccountRepository.findByNormalizedUsername(normalizedUsername).isPresent()) {
            throw new SetupAlreadyCompletedException("SETUP_ALREADY_COMPLETED");
        }

        try {
            return setupTransaction.create(command);
        } catch (DataIntegrityViolationException ex) {
            if (isUniqueConstraintViolation(ex)) {
                throw new SetupAlreadyCompletedException("SETUP_ALREADY_COMPLETED");
            }
            throw ex;
        } catch (ConstraintViolationException ex) {
            // Hibernate ConstraintViolationException not wrapped by Spring
            if (isUniqueConstraintViolationFromHibernate(ex)) {
                throw new SetupAlreadyCompletedException("SETUP_ALREADY_COMPLETED");
            }
            throw ex;
        }
    }

    private boolean isUniqueConstraintViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof SQLException sqlEx) {
                if ("23505".equals(sqlEx.getSQLState())) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean isUniqueConstraintViolationFromHibernate(ConstraintViolationException ex) {
        if (ex.getSQLException() != null) {
            return "23505".equals(ex.getSQLException().getSQLState());
        }
        return false;
    }
}
