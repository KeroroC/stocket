package com.stocket.catalog.internal.category;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CategoryController.class)
class CategoryExceptionHandler {

    @ExceptionHandler(CategoryService.CategoryNotFoundException.class)
    ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, "Category not found", "CATEGORY_NOT_FOUND");
    }

    @ExceptionHandler(CategoryService.CategoryNameConflictException.class)
    ProblemDetail nameConflict() {
        return problem(HttpStatus.CONFLICT, "Category name conflict", "CATEGORY_NAME_CONFLICT");
    }

    @ExceptionHandler(CategoryService.CategoryCycleException.class)
    ProblemDetail cycle() {
        return problem(HttpStatus.CONFLICT, "Category cycle", "CATEGORY_CYCLE");
    }

    @ExceptionHandler(CategoryService.CategoryVersionConflictException.class)
    ProblemDetail versionConflict() {
        return problem(HttpStatus.CONFLICT, "Category version conflict", "VERSION_CONFLICT");
    }

    private ProblemDetail problem(HttpStatus status, String title, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("retryable", false);
        return problem;
    }
}
