package com.stocket.audit.internal;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Immutable audit log entry persisted to the audit_log table.
 * The primary key is the event's UUID, ensuring idempotent delivery:
 * duplicate events with the same eventId are safely ignored via PK conflict.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "outcome", nullable = false, length = 16)
    private String outcome;

    @Column(name = "actor_account_id")
    private UUID actorAccountId;

    @Column(name = "subject_type", nullable = false, length = 40)
    private String subjectType;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(name = "request_id", length = 80)
    private String requestId;

    @Column(name = "source", nullable = false, length = 40)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    /**
     * No-arg constructor for JPA.
     */
    protected AuditLog() {
    }

    /**
     * Creates a new audit log entry from an audit event.
     */
    public AuditLog(UUID id, Instant occurredAt, String eventType, String outcome,
                    UUID actorAccountId, String subjectType, UUID subjectId,
                    String requestId, String source, Map<String, Object> details) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.outcome = outcome;
        this.actorAccountId = actorAccountId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.requestId = requestId;
        this.source = source;
        this.details = Map.copyOf(details);
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getEventType() {
        return eventType;
    }

    public String getOutcome() {
        return outcome;
    }

    public UUID getActorAccountId() {
        return actorAccountId;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getSource() {
        return source;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
