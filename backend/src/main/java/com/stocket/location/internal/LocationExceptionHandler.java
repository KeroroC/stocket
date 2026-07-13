package com.stocket.location.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LocationController.class)
class LocationExceptionHandler {
    @ExceptionHandler(LocationService.LocationNotFoundException.class)
    ProblemDetail notFound() { return problem(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND"); }
    @ExceptionHandler(LocationService.LocationCodeNotFoundException.class)
    ProblemDetail codeNotFound() { return problem(HttpStatus.NOT_FOUND, "LOCATION_CODE_NOT_FOUND"); }
    @ExceptionHandler(LocationService.LocationNameConflictException.class)
    ProblemDetail nameConflict() { return problem(HttpStatus.CONFLICT, "LOCATION_NAME_CONFLICT"); }
    @ExceptionHandler(LocationService.LocationCycleException.class)
    ProblemDetail cycle() { return problem(HttpStatus.CONFLICT, "LOCATION_CYCLE"); }
    @ExceptionHandler(LocationService.LocationVersionConflictException.class)
    ProblemDetail version() { return problem(HttpStatus.CONFLICT, "VERSION_CONFLICT"); }
    private ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status); problem.setTitle(code);
        problem.setProperty("code", code); problem.setProperty("retryable", false); return problem;
    }
}
