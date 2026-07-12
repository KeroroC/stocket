package com.stocket.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "normalized_username", nullable = false, unique = true, length = 64)
    private String normalizedUsername;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "credentials_changed_at", nullable = false)
    private Instant credentialsChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public UserAccount() {
    }

    public UserAccount(UUID id, String username, String normalizedUsername, String displayName,
                       String passwordHash, Instant now) {
        this.id = id;
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.status = AccountStatus.ACTIVE;
        this.mustChangePassword = false;
        this.credentialsChangedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public boolean isEnabled() {
        return status == AccountStatus.ACTIVE;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCredentialsChangedAt() {
        return credentialsChangedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        this.credentialsChangedAt = now;
        this.mustChangePassword = false;
        this.updatedAt = now;
    }
}
