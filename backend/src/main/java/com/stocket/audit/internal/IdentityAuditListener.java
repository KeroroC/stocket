package com.stocket.audit.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.stocket.identity.IdentityAuditEvent;

/**
 * Listens for {@link IdentityAuditEvent} published by the identity module
 * and persists them as immutable {@link AuditLog} entries.
 *
 * <p>Uses {@code AFTER_COMMIT} phase with {@code fallbackExecution = true}:
 * <ul>
 *   <li>Business events published inside a transaction are persisted after the outer
 *       transaction commits, ensuring the audit record is written only when the business
 *       operation succeeds.</li>
 *   <li>Events published outside a transaction (e.g., login failure) are persisted
 *       immediately via the fallback path.</li>
 * </ul>
 *
 * <p>Each event is persisted in a {@code REQUIRES_NEW} transaction, isolating the audit
 * write from the outer business transaction. The event's UUID is used as the primary key,
 * so duplicate delivery is safely ignored via primary key conflict.
 */
@Component
class IdentityAuditListener {

    private static final Logger log = LoggerFactory.getLogger(IdentityAuditListener.class);

    private final AuditLogRepository auditLogRepository;

    IdentityAuditListener(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onAuditEvent(IdentityAuditEvent event) {
        AuditLog auditLog = new AuditLog(
                event.eventId(),
                event.occurredAt(),
                event.eventType(),
                event.outcome(),
                event.actorAccountId(),
                event.subjectType(),
                event.subjectId(),
                event.requestId(),
                event.source(),
                event.details());

        try {
            auditLogRepository.save(auditLog);
        } catch (DataIntegrityViolationException ex) {
            // Duplicate event delivery — safely ignore (idempotent via PK)
            log.debug("Ignoring duplicate audit event {}: {}", event.eventType(), event.eventId());
        }
    }
}
