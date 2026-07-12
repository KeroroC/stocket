package com.stocket.identity.internal.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.identity.internal.authentication.SessionCookieService;
import com.stocket.identity.internal.authentication.SessionService;
import com.stocket.identity.internal.setup.InitializeCommand;
import com.stocket.identity.internal.setup.InitializeResult;
import com.stocket.identity.internal.setup.SetupAlreadyCompletedException;
import com.stocket.identity.internal.setup.SetupService;

@RestController
@RequestMapping("/api/v1/setup")
class SetupController {

    private final SetupService setupService;
    private final SessionService sessionService;
    private final SessionCookieService sessionCookieService;

    SetupController(SetupService setupService,
                    SessionService sessionService,
                    SessionCookieService sessionCookieService) {
        this.setupService = setupService;
        this.sessionService = sessionService;
        this.sessionCookieService = sessionCookieService;
    }

    @GetMapping("/status")
    SetupStatusResponse status() {
        return new SetupStatusResponse(setupService.isSetupCompleted());
    }

    @PostMapping("/initialize")
    ResponseEntity<?> initialize(@RequestBody InitializeRequest request,
                                 HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        if (setupService.isSetupCompleted()) {
            return conflictResponse();
        }

        try {
            InitializeResult result = setupService.initialize(
                    new InitializeCommand(
                            request.householdName(),
                            request.timezone(),
                            request.username(),
                            request.displayName(),
                            request.password()));

            // Create session for the newly created admin
            var createdSession = sessionService.create(
                    setupService.findAccountById(result.accountId()),
                    httpRequest.getHeader("User-Agent"),
                    httpRequest.getRemoteAddr(),
                    java.time.Instant.now());

            // Write session cookie
            sessionCookieService.writeSessionCookie(httpRequest, httpResponse, createdSession.token());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CurrentAccountResponse(result.accountId(), result.username(), result.role()));

        } catch (SetupAlreadyCompletedException ex) {
            return conflictResponse();
        }
    }

    private ResponseEntity<ProblemDetail> conflictResponse() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Setup already completed");
        problem.setProperty("code", "SETUP_ALREADY_COMPLETED");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
