package com.stocket.identity.internal.web;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.identity.internal.invite.InviteService;

@RestController
@RequestMapping("/api/v1/invites")
class InviteAcceptanceController {

    private final InviteService inviteService;
    private final Clock clock;

    InviteAcceptanceController(InviteService inviteService, Clock clock) {
        this.inviteService = inviteService;
        this.clock = clock;
    }

    @GetMapping("/{token}/status")
    ResponseEntity<?> getInviteStatus(@PathVariable String token) {
        try {
            InviteService.InviteStatusResult status = inviteService.getInviteStatus(token);
            return ResponseEntity.ok(new InviteStatusResponse(
                    status.available(), status.role(), status.expiresAt()));
        } catch (InviteService.InviteNotFoundException e) {
            return notFoundProblem("INVITE_NOT_FOUND", e.getMessage());
        }
    }

    @PostMapping("/{token}/accept")
    ResponseEntity<?> acceptInvite(
            @PathVariable String token,
            @Valid @RequestBody AcceptInviteRequest request,
            HttpServletRequest httpRequest) {
        String sourceAddress = httpRequest.getRemoteAddr();

        try {
            InviteService.AcceptanceResult result = inviteService.acceptInvite(
                    token, request.username(), request.displayName(),
                    request.password(), sourceAddress, clock.instant());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AcceptanceResponse(result.accountId(), result.memberId()));
        } catch (InviteService.InviteNotFoundException e) {
            return notFoundProblem("INVITE_NOT_FOUND", e.getMessage());
        } catch (InviteService.InviteNotAvailableException e) {
            return goneProblem("INVITE_NOT_AVAILABLE", e.getMessage());
        } catch (InviteService.InviteExpiredException e) {
            return goneProblem("INVITE_EXPIRED", e.getMessage());
        } catch (InviteService.AcceptRateLimitedException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
            problem.setTitle("Too many acceptance attempts");
            problem.setProperty("code", "RATE_LIMITED");
            problem.setProperty("retryable", true);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
        } catch (InviteService.DuplicateUsernameException e) {
            return conflictProblem("DUPLICATE_USERNAME", e.getMessage());
        }
    }

    private ResponseEntity<ProblemDetail> notFoundProblem(String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle(message);
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private ResponseEntity<ProblemDetail> goneProblem(String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.GONE);
        problem.setTitle(message);
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.GONE).body(problem);
    }

    private ResponseEntity<ProblemDetail> conflictProblem(String code, String message) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle(message);
        problem.setProperty("code", code);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    record AcceptanceResponse(UUID accountId, UUID memberId) {
    }
}
