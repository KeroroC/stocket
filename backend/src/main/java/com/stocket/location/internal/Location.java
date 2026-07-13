package com.stocket.location.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "location")
class Location {
    @Id private UUID id;
    @Column(name = "household_id", nullable = false) private UUID householdId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id") private Location parent;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "normalized_name", nullable = false, length = 120) private String normalizedName;
    @Column(name = "public_code", nullable = false, length = 64) private String publicCode;
    @Version @Column(nullable = false) private long version;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected Location() { }

    Location(UUID id, UUID householdId, Location parent, String name, String normalizedName,
             String publicCode, Instant now) {
        this.id = id; this.householdId = householdId; this.parent = parent; this.name = name;
        this.normalizedName = normalizedName; this.publicCode = publicCode;
        this.createdAt = now; this.updatedAt = now;
    }

    void update(Location parent, String name, String normalizedName, Instant now) {
        this.parent = parent; this.name = name; this.normalizedName = normalizedName; this.updatedAt = now;
    }
    void archive(Instant now) { archivedAt = now; updatedAt = now; }
    void restore(Instant now) { archivedAt = null; updatedAt = now; }
    UUID id() { return id; }
    Location parent() { return parent; }
    String name() { return name; }
    String normalizedName() { return normalizedName; }
    String publicCode() { return publicCode; }
    long version() { return version; }
    boolean archived() { return archivedAt != null; }
}
