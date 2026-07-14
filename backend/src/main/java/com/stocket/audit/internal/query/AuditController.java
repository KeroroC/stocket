package com.stocket.audit.internal.query;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
class AuditController {
    private final AuditQueryService service;
    AuditController(AuditQueryService service) { this.service = service; }

    @GetMapping AuditResponse search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String outcome, @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) UUID subjectId, @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "50") int size) {
        return service.search(from, to, actorId, eventType, outcome, subjectType, subjectId, requestId, cursor, size);
    }

    @ExceptionHandler(AuditQueryService.InvalidAuditQuery.class)
    ProblemDetail invalid() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Invalid audit query"); problem.setProperty("code", "AUDIT_QUERY_INVALID");
        problem.setProperty("retryable", false); return problem;
    }
}
