package com.stocket.catalog.internal.item;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.stocket.catalog.internal.category.CategoryItemPolicy;

@RestControllerAdvice(assignableTypes = ItemController.class)
class ItemExceptionHandler {
    @ExceptionHandler(ItemService.ItemNotFoundException.class)
    ProblemDetail itemNotFound() { return problem(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND"); }

    @ExceptionHandler(CategoryItemPolicy.CategoryNotFoundException.class)
    ProblemDetail categoryNotFound() { return problem(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND"); }

    @ExceptionHandler(CategoryItemPolicy.CategoryArchivedException.class)
    ProblemDetail categoryArchived() { return problem(HttpStatus.CONFLICT, "CATEGORY_ARCHIVED"); }

    @ExceptionHandler(ItemService.BarcodeConflictException.class)
    ProblemDetail barcodeConflict() { return problem(HttpStatus.CONFLICT, "BARCODE_CONFLICT"); }

    @ExceptionHandler(ItemService.TagConflictException.class)
    ProblemDetail tagConflict() { return problem(HttpStatus.CONFLICT, "TAG_CONFLICT"); }

    @ExceptionHandler(ItemService.VersionConflictException.class)
    ProblemDetail versionConflict() { return problem(HttpStatus.CONFLICT, "VERSION_CONFLICT"); }

    @ExceptionHandler({ItemService.InvalidCollectionValueException.class, ItemService.InvalidShelfLifeException.class,
            CategoryItemPolicy.InvalidItemAttributesException.class})
    ProblemDetail invalidRequest(RuntimeException exception) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "ITEM_INVALID");
        problem.setDetail(exception.getMessage());
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(code);
        problem.setProperty("code", code);
        problem.setProperty("retryable", false);
        return problem;
    }
}
