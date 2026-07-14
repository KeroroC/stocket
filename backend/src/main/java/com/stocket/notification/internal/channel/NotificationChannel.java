package com.stocket.notification.internal.channel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_channel")
public class NotificationChannel {

    @Id
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(nullable = false, length = 24)
    private String type;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    @Column(name = "encrypted_secret")
    private String encryptedSecret;

    @Column(name = "key_version")
    private Integer keyVersion;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationChannel() {
    }

    NotificationChannel(UUID id, UUID householdId, String type, boolean enabled,
                        Map<String, Object> configuration, Instant now) {
        this.id = id;
        this.householdId = householdId;
        this.type = type;
        this.enabled = enabled;
        this.configuration = new LinkedHashMap<>(configuration);
        this.createdAt = now;
        this.updatedAt = now;
    }

    void update(boolean enabled, Map<String, Object> configuration, Instant now) {
        this.enabled = enabled;
        this.configuration = new LinkedHashMap<>(configuration);
        this.updatedAt = now;
    }

    void changeSecret(String encryptedSecret, int keyVersion, Instant now) {
        this.encryptedSecret = encryptedSecret;
        this.keyVersion = keyVersion;
        this.updatedAt = now;
    }

    UUID id() { return id; }
    UUID householdId() { return householdId; }
    String type() { return type; }
    boolean enabled() { return enabled; }
    Map<String, Object> configuration() { return Map.copyOf(configuration); }
    boolean hasSecret() { return encryptedSecret != null; }
    long version() { return version; }
}
