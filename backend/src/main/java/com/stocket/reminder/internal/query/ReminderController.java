package com.stocket.reminder.internal.query;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reminders")
class ReminderController {

    private final ReminderQueryService service;

    ReminderController(ReminderQueryService service) {
        this.service = service;
    }

    @GetMapping
    ReminderQueryService.ReminderPage list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, type, from, to, page, size);
    }

    @PostMapping("/{id}/acknowledge")
    ReminderResponse acknowledge(@PathVariable UUID id) {
        return service.acknowledge(id);
    }
}
