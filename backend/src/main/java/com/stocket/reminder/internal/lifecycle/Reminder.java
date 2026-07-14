package com.stocket.reminder.internal.lifecycle;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "reminder")
public class Reminder {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "item_definition_id", nullable = false)
    private UUID itemDefinitionId;

    @Column(name = "inventory_entry_id")
    private UUID inventoryEntryId;

    @Column(name = "reminder_type", nullable = false, length = 24)
    private String reminderType;

    @Column(name = "trigger_key", nullable = false, length = 80)
    private String triggerKey;

    @Column(name = "trigger_at", nullable = false)
    private Instant triggerAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reminder() {
    }

    public boolean matches(String type, String key, UUID entryId) {
        return reminderType.equals(type)
                && triggerKey.equals(key)
                && java.util.Objects.equals(inventoryEntryId, entryId);
    }

    public void resolve(Instant now) {
        status = "RESOLVED";
        resolvedAt = now;
        updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID inventoryEntryId() {
        return inventoryEntryId;
    }

    public String reminderType() {
        return reminderType;
    }

    public String triggerKey() {
        return triggerKey;
    }
}
