package com.stocket.system.diagnostics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/diagnostics")
class DiagnosticsController {
    private final DiagnosticsService service;
    DiagnosticsController(DiagnosticsService service) { this.service = service; }
    @GetMapping DiagnosticsResponse diagnostics() { return service.inspect(); }
}
