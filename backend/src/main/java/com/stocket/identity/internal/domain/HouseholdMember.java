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
import jakarta.persistence.UniqueConstraint;

import com.stocket.identity.IdentityRole;

@Entity
@Table(name = "household_member", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"household_id", "account_id"})
})
public class HouseholdMember {

    @Id
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private IdentityRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public HouseholdMember() {
    }

    public HouseholdMember(UUID id, Household household, UserAccount account, IdentityRole role, Instant now) {
        this.id = id;
        this.household = household;
        this.account = account;
        this.role = role;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
    }

    public UserAccount getAccount() {
        return account;
    }

    public IdentityRole getRole() {
        return role;
    }
}
