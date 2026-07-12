package com.stocket.identity.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "household")
public class Household {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "singleton_key", nullable = false)
    private short singletonKey = 1;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "timezone", nullable = false, length = 80)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Household() {
    }

    public Household(UUID id, String name, String timezone, Instant now) {
        this.id = id;
        this.name = name;
        this.timezone = timezone;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }
}
