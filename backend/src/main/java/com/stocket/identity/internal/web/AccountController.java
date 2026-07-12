package com.stocket.identity.internal.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.identity.internal.account.AccountService;
import com.stocket.identity.internal.authentication.CreatedSession;
import com.stocket.identity.internal.authentication.SessionCookieService;
import com.stocket.identity.internal.security.IdentityPrincipal;

@RestController
@RequestMapping("/api/v1/account")
class AccountController {

    private final AccountService accountService;
    private final SessionCookieService sessionCookieService;

    AccountController(AccountService accountService,
                      SessionCookieService sessionCookieService) {
        this.accountService = accountService;
        this.sessionCookieService = sessionCookieService;
    }

    @GetMapping
    ResponseEntity<AccountResponse> getAccount(@AuthenticationPrincipal IdentityPrincipal principal) {
        AccountResponse response = accountService.getAccount(principal.accountId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/profile")
    ResponseEntity<AccountResponse> updateProfile(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        AccountResponse response = accountService.updateProfile(
                principal.accountId(),
                request.displayName(),
                request.email());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password")
    ResponseEntity<?> changePassword(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            Instant now = Instant.now();
            CreatedSession newSession = accountService.changePassword(
                    principal.accountId(),
                    request.oldPassword(),
                    request.newPassword(),
                    httpRequest.getHeader("User-Agent"),
                    httpRequest.getRemoteAddr(),
                    now);

            // Write new session cookie
            sessionCookieService.writeSessionCookie(httpRequest, httpResponse, newSession.token());

            return ResponseEntity.ok().build();
        } catch (AccountService.InvalidOldPasswordException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            problem.setTitle("Invalid old password");
            problem.setProperty("code", "INVALID_CREDENTIALS");
            problem.setProperty("retryable", false);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
        } catch (AccountService.PasswordPolicyViolationException e) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
            problem.setTitle("Password policy violation");
            problem.setProperty("code", "PASSWORD_POLICY_VIOLATION");
            problem.setProperty("retryable", false);
            problem.setProperty("violations", e.getViolations());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
        }
    }

    @GetMapping("/sessions")
    ResponseEntity<List<SessionResponse>> listSessions(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        Instant now = Instant.now();
        List<SessionResponse> sessions = accountService.listSessions(
                principal.accountId(), principal.sessionId(), now);
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal IdentityPrincipal principal,
            @PathVariable UUID sessionId) {
        boolean revoked = accountService.revokeSession(
                principal.accountId(), sessionId, Instant.now());
        if (revoked) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/sessions/others")
    ResponseEntity<Void> revokeOtherSessions(
            @AuthenticationPrincipal IdentityPrincipal principal) {
        accountService.revokeOtherSessions(
                principal.accountId(), principal.sessionId(), Instant.now());
        return ResponseEntity.noContent().build();
    }
}
