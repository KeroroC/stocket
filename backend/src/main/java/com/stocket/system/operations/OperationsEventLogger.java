package com.stocket.system.operations;

import com.stocket.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OperationsEventLogger {
    private static final Logger log = LoggerFactory.getLogger(OperationsEventLogger.class);

    @EventListener
    public void on(AuditEvent event) {
        try (MDC.MDCCloseable request = MDC.putCloseable("requestId", event.requestId());
             MDC.MDCCloseable account = event.actorAccountId() == null ? null
                     : MDC.putCloseable("accountId", event.actorAccountId().toString())) {
            log.atInfo()
                    .addKeyValue("operation", event.eventType())
                    .addKeyValue("outcome", event.outcome())
                    .addKeyValue("source", event.source())
                    .log("business operation completed");
        }
    }
}
