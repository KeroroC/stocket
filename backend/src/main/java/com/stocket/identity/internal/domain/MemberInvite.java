package com.stocket.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.stocket.identity.IdentityRole;

@Entity
@Table(name = "member_invite")
public class MemberInvite {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "bpchar(64)")
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private IdentityRole role;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "max_uses", nullable = false)
    private Integer maxUses = 1;

    @Column(name = "use_count", nullable = false)
    private Integer useCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private UserAccount createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private UserAccount acceptedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MemberInvite() {
    }

    public MemberInvite(UUID id, Household household, String tokenHash,
                         IdentityRole role, Instant expiresAt,
                         UserAccount createdBy, Instant createdAt) {
        this.id = id;
        this.household = household;
        this.tokenHash = tokenHash;
        this.role = role;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public IdentityRole getRole() {
        return role;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UserAccount getCreatedBy() {
        return createdBy;
    }

    public UserAccount getAcceptedBy() {
        return acceptedBy;
    }

    public void setAcceptedBy(UserAccount acceptedBy) {
        this.acceptedBy = acceptedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isAvailable() {
        return acceptedAt == null && revokedAt == null && useCount < maxUses;
    }

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public Integer getUseCount() {
        return useCount;
    }

    public void setUseCount(Integer useCount) {
        this.useCount = useCount;
    }
}
