package com.stocket.inventory.internal.web;

import com.stocket.inventory.internal.command.InventoryCommandException;
import com.stocket.inventory.internal.domain.InventoryRuleViolationException;
import com.stocket.inventory.internal.idempotency.IdempotentExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InventoryCommandController.class)
public class InventoryExceptionHandler {

    @ExceptionHandler(IdempotentExecutor.IdempotencyException.class)
    ProblemDetail idempotency(IdempotentExecutor.IdempotencyException exception) {
        return problem(exception.status(), exception.code());
    }

    @ExceptionHandler(InventoryCommandException.class)
    ProblemDetail command(InventoryCommandException exception) {
        return problem(exception.status(), exception.code());
    }

    @ExceptionHandler(InventoryRuleViolationException.class)
    ProblemDetail domain(InventoryRuleViolationException exception) {
        HttpStatus status = switch (exception.code()) {
            case "INSUFFICIENT_STOCK", "ENTRY_ARCHIVED", "SAME_LOCATION" -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return problem(status, exception.code());
    }

    private ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(code);
        problem.setProperty("code", code);
        problem.setProperty("retryable", "IDEMPOTENCY_IN_PROGRESS".equals(code));
        return problem;
    }
}
