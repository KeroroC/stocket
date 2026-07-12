package com.stocket.identity.internal.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.stocket.identity.internal.authentication.SessionCookieService;
import com.stocket.identity.internal.authentication.SessionService;

@Component
class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "STOCKET_SESSION";

    private final SessionService sessionService;
    private final SessionCookieService sessionCookieService;
    private final RequestAttributeSecurityContextRepository securityContextRepository;

    SessionAuthenticationFilter(SessionService sessionService,
                                SessionCookieService sessionCookieService) {
        this.sessionService = sessionService;
        this.sessionCookieService = sessionCookieService;
        this.securityContextRepository = new RequestAttributeSecurityContextRepository();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rawToken = extractToken(request);

        if (rawToken != null) {
            try {
                sessionService.authenticate(rawToken, Instant.now())
                        .ifPresent(principal -> {
                            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                                    principal,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            securityContextRepository.saveContext(
                                    SecurityContextHolder.getContext(), request, response);
                        });
            } catch (Exception e) {
                // Invalid token - clear cookie and continue as anonymous
                sessionCookieService.clearSessionCookie(request, response);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
