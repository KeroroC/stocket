package com.stocket.attachment.internal.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    Optional<Attachment> findByHouseholdIdAndId(UUID householdId, UUID id);
    List<Attachment> findByHouseholdIdAndOwnerTypeAndOwnerIdAndStatusOrderByCreatedAtDesc(
            UUID householdId, String ownerType, UUID ownerId, AttachmentStatus status);
    List<Attachment> findByStatus(AttachmentStatus status);
    List<Attachment> findByHouseholdIdAndOwnerTypeAndOwnerIdAndPurposeAndStatus(
            UUID householdId, String ownerType, UUID ownerId, AttachmentPurpose purpose, AttachmentStatus status);
}
