package com.stocket.system;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLockingFailure() {
        ProblemDetail problem = ProblemDetail.forStatus(409);
        problem.setTitle("Version conflict");
        problem.setProperty("code", "VERSION_CONFLICT");
        problem.setProperty("retryable", false);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @RegisterReflectionForBinding(FieldError.class)
    ProblemDetail handleValidationError(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(422);
        problem.setTitle("Validation failed");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("retryable", false);
        problem.setProperty("fieldErrors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .toList());
        return problem;
    }

    private record FieldError(String field, String message) {
    }
}
