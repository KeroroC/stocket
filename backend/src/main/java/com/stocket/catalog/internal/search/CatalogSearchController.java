package com.stocket.catalog.internal.search;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/search")
class CatalogSearchController {
    private final CatalogSearchService service;
    CatalogSearchController(CatalogSearchService service) { this.service = service; }
    @GetMapping CatalogSearchResult search(@RequestParam String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return service.search(q, page, size, includeArchived);
    }
    @ExceptionHandler(CatalogSearchService.InvalidSearchException.class)
    ProblemDetail invalid() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Invalid search"); problem.setProperty("code", "SEARCH_INVALID");
        problem.setProperty("retryable", false); return problem;
    }
}
