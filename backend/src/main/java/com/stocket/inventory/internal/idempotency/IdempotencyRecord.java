package com.stocket.inventory.internal.idempotency;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    private UUID id;
    @Column(name = "household_id", nullable = false)
    private UUID householdId;
    @Column(name = "account_id", nullable = false)
    private UUID accountId;
    @Column(nullable = false, length = 48)
    private String operation;
    @Column(name = "idempotency_key", nullable = false, length = 120)
    private String idempotencyKey;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "http_status")
    private Integer httpStatus;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private JsonNode responseBody;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public UUID id() {
        return id;
    }
}
