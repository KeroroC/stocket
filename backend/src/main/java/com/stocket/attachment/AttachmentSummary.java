package com.stocket.attachment;

import java.time.Instant;
import java.util.UUID;

@org.springframework.modulith.NamedInterface("api")
public record AttachmentSummary(UUID id, String ownerType, UUID ownerId, String purpose,
                                String filename, String mediaType, long sizeBytes,
                                String status, Instant createdAt) {}
