package com.stocket.attachment.internal.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "attachment")
public class Attachment {
    @Id private UUID id;
    @Column(name="household_id", nullable=false) private UUID householdId;
    @Column(name="owner_type", nullable=false) private String ownerType;
    @Column(name="owner_id", nullable=false) private UUID ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private AttachmentPurpose purpose;
    @Column(name="original_filename", nullable=false) private String originalFilename;
    @Column(name="storage_key", nullable=false, unique=true) private String storageKey;
    @Column(name="detected_media_type", nullable=false) private String detectedMediaType;
    @Column(name="size_bytes", nullable=false) private long sizeBytes;
    @Column(nullable=false, length=64) private String sha256;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private AttachmentStatus status;
    @Column(name="uploaded_by", nullable=false) private UUID uploadedBy;
    @Column(name="request_id", nullable=false) private String requestId;
    @Version private long version;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="deleted_at") private Instant deletedAt;

    protected Attachment() {}
    public Attachment(UUID id, UUID householdId, String ownerType, UUID ownerId, AttachmentPurpose purpose,
                      String originalFilename, String storageKey, String detectedMediaType, long sizeBytes,
                      String sha256, UUID uploadedBy, String requestId) {
        this.id=id; this.householdId=householdId; this.ownerType=ownerType; this.ownerId=ownerId; this.purpose=purpose;
        this.originalFilename=originalFilename; this.storageKey=storageKey; this.detectedMediaType=detectedMediaType;
        this.sizeBytes=sizeBytes; this.sha256=sha256; this.status=AttachmentStatus.STAGED; this.uploadedBy=uploadedBy;
        this.requestId=requestId; this.createdAt=Instant.now();
    }
    public void available(){ status=AttachmentStatus.AVAILABLE; }
    public void missing(){ status=AttachmentStatus.MISSING; }
    public void deleted(){ status=AttachmentStatus.DELETED; deletedAt=Instant.now(); }
    public UUID getId(){return id;} public UUID getHouseholdId(){return householdId;} public String getOwnerType(){return ownerType;}
    public UUID getOwnerId(){return ownerId;} public AttachmentPurpose getPurpose(){return purpose;} public String getOriginalFilename(){return originalFilename;}
    public String getStorageKey(){return storageKey;} public String getDetectedMediaType(){return detectedMediaType;} public long getSizeBytes(){return sizeBytes;}
    public AttachmentStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
}
