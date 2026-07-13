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

    @ExceptionHandler(CategoryService.CategoryNotEmptyException.class)
    ProblemDetail notEmpty() {
        return problem(HttpStatus.CONFLICT, "Category is not empty", "CATEGORY_NOT_EMPTY");
    }

    @ExceptionHandler(AttributeValidationException.class)
    ProblemDetail invalidSchema(AttributeValidationException exception) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Invalid attribute schema", "ATTRIBUTE_SCHEMA_INVALID");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    @ExceptionHandler(CategoryService.AttributeSchemaIncompatibleException.class)
    ProblemDetail incompatibleSchema(CategoryService.AttributeSchemaIncompatibleException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT,
                "Attribute schema is incompatible", "ATTRIBUTE_SCHEMA_INCOMPATIBLE");
        problem.setDetail("Item " + exception.itemId() + " is incompatible at key " + exception.key());
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String title, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("code", code);
        problem.setProperty("retryable", false);
        return problem;
    }
}
