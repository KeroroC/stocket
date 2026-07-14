package com.stocket.system.export;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

import com.stocket.catalog.CatalogFilter;
import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.identity.IdentityRole;
import com.stocket.inventory.InventoryFilter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/exports")
class ExportController {
    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private final CsvExportService service;
    private final CurrentHouseholdProvider current;

    ExportController(CsvExportService service, CurrentHouseholdProvider current) {
        this.service = service; this.current = current;
    }

    @GetMapping(value = "/catalog.csv", produces = "text/csv")
    ResponseEntity<StreamingResponseBody> catalog(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) UUID categoryId,
                                                   @RequestParam(defaultValue = "false") boolean includeArchived) {
        CurrentHousehold context = current.requireCurrent();
        CatalogFilter filter = new CatalogFilter(q, categoryId, includeArchived);
        validate(filter);
        service.assertCatalogLimit(context.householdId(), filter);
        return response("catalog.csv", output -> service.writeCatalog(context.householdId(), filter, output));
    }

    @GetMapping(value = "/inventory.csv", produces = "text/csv")
    ResponseEntity<StreamingResponseBody> inventory(@RequestParam(required = false) UUID itemId,
                                                     @RequestParam(required = false) UUID locationId,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) String assetStatus,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresFrom,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresTo,
                                                     @RequestParam(defaultValue = "false") boolean includeArchived) {
        CurrentHousehold context = current.requireCurrent();
        if (includeArchived && context.role() != IdentityRole.ADMIN) throw new ExportProblem(HttpStatus.FORBIDDEN, "FORBIDDEN");
        InventoryFilter filter = new InventoryFilter(itemId, locationId, type, assetStatus, expiresFrom, expiresTo, includeArchived);
        validate(filter);
        service.assertInventoryLimit(context.householdId(), filter);
        return response("inventory.csv", output -> service.writeInventory(context.householdId(), filter, output));
    }

    private ResponseEntity<StreamingResponseBody> response(String filename, StreamingResponseBody body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(CSV);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setCacheControl(CacheControl.noStore().cachePrivate());
        headers.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private void validate(CatalogFilter filter) {
        try { filter.validate(false); } catch (IllegalArgumentException error) { throw invalid(); }
    }
    private void validate(InventoryFilter filter) {
        try { filter.validate(); } catch (IllegalArgumentException error) { throw invalid(); }
    }
    private ExportProblem invalid() { return new ExportProblem(HttpStatus.UNPROCESSABLE_ENTITY, "EXPORT_FILTER_INVALID"); }

    @ExceptionHandler(ExportProblem.class)
    ProblemDetail problem(ExportProblem error) {
        ProblemDetail problem = ProblemDetail.forStatus(error.status());
        problem.setTitle("Export error");
        problem.setProperty("code", error.code());
        problem.setProperty("retryable", false);
        return problem;
    }
}
