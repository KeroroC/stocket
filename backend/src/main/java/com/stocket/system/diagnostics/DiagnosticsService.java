package com.stocket.system.diagnostics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.stocket.identity.CurrentHouseholdProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DiagnosticsService {
    private final JdbcTemplate jdbc;
    private final CurrentHouseholdProvider current;
    private final Path attachmentDirectory;

    DiagnosticsService(JdbcTemplate jdbc, CurrentHouseholdProvider current,
                       @Value("${stocket.attachment.directory}") String attachmentDirectory) {
        this.jdbc = jdbc; this.current = current; this.attachmentDirectory = Path.of(attachmentDirectory);
    }

    @Transactional(readOnly = true)
    DiagnosticsResponse inspect() {
        UUID householdId = current.requireCurrent().householdId();
        Instant checkedAt = Instant.now();
        Map<String, DiagnosticsResponse.Check> checks = new LinkedHashMap<>();
        boolean databaseReady = databaseReady();
        checks.put("database", check(databaseReady ? "OK" : "ERROR", databaseReady ? 0 : 1, checkedAt, "CHECK_DATABASE"));
        boolean storageReady = Files.isDirectory(attachmentDirectory) && Files.isWritable(attachmentDirectory);
        checks.put("attachmentStorage", check(storageReady ? "OK" : "ERROR", storageReady ? 0 : 1, checkedAt, "CHECK_ATTACHMENT_STORAGE"));
        checks.put("incompleteEvents", count("select count(*) from event_publication where completion_date is null", new Object[]{}, checkedAt, "REPUBLISH_MODULE_EVENTS"));
        checks.put("deadDeliveries", count("select count(*) from notification_delivery where household_id=? and status='DEAD'", new Object[]{householdId}, checkedAt, "RETRY_DEAD_DELIVERIES"));
        checks.put("openReconciliation", count("select count(*) from inventory_reconciliation_issue where household_id=? and status='OPEN'", new Object[]{householdId}, checkedAt, "RUN_INVENTORY_RECONCILIATION"));
        checks.put("missingAttachments", count("select count(*) from attachment where household_id=? and status='MISSING'", new Object[]{householdId}, checkedAt, "REPAIR_ATTACHMENT_STORAGE"));
        return new DiagnosticsResponse(Map.copyOf(checks));
    }

    private boolean databaseReady() { return Integer.valueOf(1).equals(jdbc.queryForObject("select 1", Integer.class)); }
    private DiagnosticsResponse.Check count(String sql, Object[] arguments, Instant checkedAt, String actionCode) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        long count = value == null ? 0 : value;
        return check(count == 0 ? "OK" : "WARN", count, checkedAt, actionCode);
    }
    private DiagnosticsResponse.Check check(String status, long count, Instant checkedAt, String actionCode) {
        return new DiagnosticsResponse.Check(status, count, checkedAt, actionCode);
    }
}
