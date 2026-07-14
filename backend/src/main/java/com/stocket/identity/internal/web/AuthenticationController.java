package com.stocket.identity.internal.web;

import java.time.Instant;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stocket.identity.IdentityRole;
import com.stocket.identity.ClientAddressProvider;
import com.stocket.identity.internal.authentication.AuthenticationService;
import com.stocket.identity.internal.authentication.AuthenticationService.LoginResult;
import com.stocket.identity.internal.authentication.SessionCookieService;
import com.stocket.identity.internal.domain.HouseholdMember;
import com.stocket.identity.internal.persistence.HouseholdMemberRepository;

@RestController
@RequestMapping("/api/v1/auth")
class AuthenticationController {

    private static final String SESSION_COOKIE_NAME = "STOCKET_SESSION";

    private final AuthenticationService authenticationService;
    private final SessionCookieService sessionCookieService;
    private final CsrfTokenRepository csrfTokenRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final ObjectProvider<ClientAddressProvider> clientAddresses;

    AuthenticationController(AuthenticationService authenticationService,
                              SessionCookieService sessionCookieService,
                              CsrfTokenRepository csrfTokenRepository,
                              HouseholdMemberRepository householdMemberRepository,
                              ObjectProvider<ClientAddressProvider> clientAddresses) {
        this.authenticationService = authenticationService;
        this.sessionCookieService = sessionCookieService;
        this.csrfTokenRepository = csrfTokenRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.clientAddresses = clientAddresses;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                            HttpServletRequest httpRequest,
                            HttpServletResponse httpResponse) {
        Instant now = Instant.now();
        String userAgent = httpRequest.getHeader("User-Agent");
        String sourceAddress = clientAddresses.getIfAvailable(() -> HttpServletRequest::getRemoteAddr).resolve(httpRequest);

        LoginResult result = authenticationService.login(
                request.username(), request.password(),
                userAgent, sourceAddress, now);

        return switch (result) {
            case LoginResult.Success success -> {
                // Write session cookie
                sessionCookieService.writeSessionCookie(
                        httpRequest, httpResponse, success.session().token());

                // Rotate CSRF token (clears old, generates new)
                CsrfToken newToken = csrfTokenRepository.generateToken(httpRequest);
                csrfTokenRepository.saveToken(newToken, httpRequest, httpResponse);

                // Resolve role from household membership
                IdentityRole role = householdMemberRepository
                        .findByAccountId(success.accountId())
                        .map(HouseholdMember::getRole)
                        .orElse(IdentityRole.VIEWER);

                yield ResponseEntity.ok(
                        new CurrentAccountResponse(
                                success.accountId(), success.username(), role));
            }
            case LoginResult.Failure failure -> failureResponse(failure.code());
        };
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest httpRequest,
                                 HttpServletResponse httpResponse) {
        // Extract session token before clearing cookie
        String rawToken = extractSessionToken(httpRequest);

        // Revoke session in database
        authenticationService.logout(rawToken, Instant.now());

        // Clear session cookie
        sessionCookieService.clearSessionCookie(httpRequest, httpResponse);

        // Clear CSRF token (idempotent - works even if no session)
        CsrfLogoutHandler csrfLogoutHandler = new CsrfLogoutHandler(csrfTokenRepository);
        csrfLogoutHandler.logout(httpRequest, httpResponse, null);

        return ResponseEntity.noContent().build();
    }

    private String extractSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (SESSION_COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private ResponseEntity<ProblemDetail> failureResponse(String code) {
        HttpStatus status = "RATE_LIMITED".equals(code)
                ? HttpStatus.TOO_MANY_REQUESTS
                : HttpStatus.UNAUTHORIZED;

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(status == HttpStatus.TOO_MANY_REQUESTS
                ? "Too many login attempts"
                : "Invalid credentials");
        problem.setProperty("code", code);
        problem.setProperty("retryable", false);

        return ResponseEntity.status(status).body(problem);
    }
}
