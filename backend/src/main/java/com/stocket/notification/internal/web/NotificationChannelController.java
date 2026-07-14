package com.stocket.notification.internal.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.notification.internal.channel.ChannelService;

@RestController
@RequestMapping("/api/v1/notification/channels")
@PreAuthorize("hasRole('ADMIN')")
class NotificationChannelController {

    private final ChannelService service;

    NotificationChannelController(ChannelService service) {
        this.service = service;
    }

    @GetMapping
    List<ChannelResponse> list() {
        return service.list();
    }

    @PutMapping("/{type}")
    ChannelResponse upsert(@PathVariable String type, @RequestBody ChannelRequest request) {
        return service.upsert(type, request.enabled(), request.configuration(),
                request.secret(), request.version());
    }

    @PostMapping("/{id}/test")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void test(@PathVariable UUID id) {
        service.test(id);
    }

    @ExceptionHandler(ChannelService.InvalidChannelException.class)
    ProblemDetail invalid() {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "CHANNEL_INVALID");
    }

    @ExceptionHandler(ChannelService.VersionConflictException.class)
    ProblemDetail versionConflict() {
        return problem(HttpStatus.CONFLICT, "VERSION_CONFLICT");
    }

    @ExceptionHandler(ChannelService.RateLimitedException.class)
    ProblemDetail rateLimited() {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "CHANNEL_TEST_RATE_LIMITED");
    }

    @ExceptionHandler(ChannelService.ChannelNotFoundException.class)
    ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, "CHANNEL_NOT_FOUND");
    }

    private ProblemDetail problem(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(code);
        problem.setProperty("code", code);
        problem.setProperty("retryable", false);
        return problem;
    }
}
