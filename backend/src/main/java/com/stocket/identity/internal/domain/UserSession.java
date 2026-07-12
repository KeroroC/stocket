package com.stocket.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_session")
public class UserSession {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "bpchar(64)")
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "idle_expires_at", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 40)
    private String revokeReason;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "source_address", length = 64)
    private String sourceAddress;

    public UserSession() {
    }

    public UserSession(UUID id, UserAccount account, String tokenHash,
                       Instant now, Instant idleExpiresAt, Instant absoluteExpiresAt,
                       String userAgent, String sourceAddress) {
        this.id = id;
        this.account = account;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.idleExpiresAt = idleExpiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
        this.userAgent = userAgent;
        this.sourceAddress = sourceAddress;
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getAccount() {
        return account;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getIdleExpiresAt() {
        return idleExpiresAt;
    }

    public Instant getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void touch(Instant now, Instant newIdleExpiresAt) {
        this.lastSeenAt = now;
        this.idleExpiresAt = newIdleExpiresAt;
    }

    public void revoke(String reason, Instant now) {
        this.revokedAt = now;
        this.revokeReason = reason;
    }
}
