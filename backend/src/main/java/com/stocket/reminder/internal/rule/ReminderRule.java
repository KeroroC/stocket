package com.stocket.reminder.internal.rule;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reminder_rule")
public class ReminderRule {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Column(name = "scope_id")
    private UUID scopeId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "expiration_offsets", nullable = false, columnDefinition = "integer[]")
    private Integer[] expirationOffsets;

    @Column(name = "low_stock_threshold", precision = 19, scale = 4)
    private BigDecimal lowStockThreshold;

    @Column(nullable = false)
    private boolean enabled;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReminderRule() {
    }

    private ReminderRule(UUID householdId, String scopeType, UUID scopeId,
                         EffectiveReminderRule effectiveRule, Instant now) {
        this.id = UUID.randomUUID();
        this.householdId = householdId;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
        this.expirationOffsets = effectiveRule.expirationOffsets().toArray(Integer[]::new);
        this.lowStockThreshold = effectiveRule.lowStockThreshold().orElse(null);
        this.enabled = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ReminderRule create(UUID householdId, String scopeType, UUID scopeId,
                                      List<Integer> expirationOffsets, BigDecimal lowStockThreshold) {
        if (householdId == null) {
            throw new IllegalArgumentException("Household is required");
        }
        if (!List.of("HOUSEHOLD", "CATEGORY", "ITEM").contains(scopeType)) {
            throw new IllegalArgumentException("Unsupported reminder rule scope");
        }
        if (("HOUSEHOLD".equals(scopeType) && scopeId != null)
                || (!"HOUSEHOLD".equals(scopeType) && scopeId == null)) {
            throw new IllegalArgumentException("Scope id does not match scope type");
        }
        return new ReminderRule(householdId, scopeType, scopeId,
                new EffectiveReminderRule(expirationOffsets, lowStockThreshold), Instant.now());
    }

    public String scopeType() {
        return scopeType;
    }

    public UUID scopeId() {
        return scopeId;
    }

    public EffectiveReminderRule effectiveRule() {
        return new EffectiveReminderRule(List.of(expirationOffsets), lowStockThreshold);
    }
}
