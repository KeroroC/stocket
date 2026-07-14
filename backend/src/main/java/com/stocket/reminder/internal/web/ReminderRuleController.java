package com.stocket.reminder.internal.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.reminder.internal.rule.ReminderRuleService;

@RestController
@RequestMapping("/api/v1/reminder-rules")
class ReminderRuleController {

    private final ReminderRuleService service;

    ReminderRuleController(ReminderRuleService service) {
        this.service = service;
    }

    @GetMapping
    List<ReminderRuleService.RuleView> list() {
        return service.list();
    }

    @PutMapping("/{scopeType}/{scopeId}")
    @PreAuthorize("hasRole('ADMIN')")
    ReminderRuleService.RuleView update(@PathVariable String scopeType, @PathVariable UUID scopeId,
                                        @Valid @RequestBody ReminderRuleRequest request) {
        return service.upsert(scopeType, scopeId, request.expirationOffsets(),
                request.lowStockThreshold(), request.enabled(), request.version());
    }

    @ExceptionHandler(ReminderRuleService.VersionConflictException.class)
    ProblemDetail versionConflict() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("VERSION_CONFLICT");
        problem.setProperty("code", "VERSION_CONFLICT");
        problem.setProperty("retryable", false);
        return problem;
    }
}
