package com.stocket.audit.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEventTypeOrderByOccurredAtDesc(String eventType);

    List<AuditLog> findBySubjectTypeAndSubjectIdOrderByOccurredAtDesc(
            String subjectType, UUID subjectId);
}
