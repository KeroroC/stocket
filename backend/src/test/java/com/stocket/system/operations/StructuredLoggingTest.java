package com.stocket.system.operations;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.stocket.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggingTest {
    @Test void logsOnlyFixedOperationalFieldsAndCorrelationContext() {
        Logger logger = (Logger) LoggerFactory.getLogger(OperationsEventLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start(); logger.addAppender(appender);
        try {
            new OperationsEventLogger().on(new AuditEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                    "InventoryReceived", "SUCCESS", UUID.randomUUID(), "INVENTORY_ENTRY", UUID.randomUUID(),
                    "request-safe-123", "api", Map.of("password", "must-not-appear", "body", "secret-body")));
        } finally {
            logger.detachAppender(appender);
        }

        ILoggingEvent event = appender.list.getFirst();
        assertThat(event.getFormattedMessage()).isEqualTo("business operation completed");
        assertThat(event.getKeyValuePairs()).extracting(pair -> pair.key)
                .containsExactlyInAnyOrder("operation", "outcome", "source");
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-safe-123").containsKey("accountId");
        assertThat(event.toString()).doesNotContain("must-not-appear", "secret-body", "password", "body");
    }
}
