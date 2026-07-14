package com.stocket.audit.internal.listener;

import java.util.List;
import java.util.UUID;

import com.stocket.audit.AuditEvent;
import com.stocket.audit.internal.AuditLog;
import com.stocket.audit.internal.AuditLogRepository;
import com.stocket.audit.internal.domain.AuditDetailsPolicy;
import com.stocket.identity.IdentityAuditEvent;
import com.stocket.identity.RequestContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AuditEventListener {
    private final AuditLogRepository repository;
    private final AuditDetailsPolicy policy;
    private final JdbcTemplate jdbc;

    public AuditEventListener(AuditLogRepository repository, AuditDetailsPolicy policy, JdbcTemplate jdbc) {
        this.repository = repository; this.policy = policy; this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRED)
    public void onAuditEvent(AuditEvent event) {
        if (repository.existsById(event.eventId())) return;
        repository.saveAndFlush(new AuditLog(event.eventId(), event.householdId(), event.occurredAt(), event.eventType(),
                event.outcome(), event.actorAccountId(), event.subjectType(), event.subjectId(), event.requestId(),
                event.source(), policy.sanitize(event.eventType(), event.details())));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRED)
    public void onIdentityAuditEvent(IdentityAuditEvent event) {
        List<UUID> households = jdbc.query("select id from household order by created_at limit 1",
                (resultSet, row) -> resultSet.getObject("id", UUID.class));
        String requestId = event.requestId() != null ? event.requestId()
                : RequestContext.currentRequestId().orElse(event.eventId().toString());
        onAuditEvent(new AuditEvent(event.eventId(), households.isEmpty() ? null : households.getFirst(), event.occurredAt(),
                event.eventType(), event.outcome(), event.actorAccountId(), event.subjectType(), event.subjectId(),
                requestId, event.source(), event.details()));
    }
}
