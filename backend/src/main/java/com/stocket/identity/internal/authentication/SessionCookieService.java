package com.stocket.identity.internal.authentication;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.stocket.identity.internal.config.IdentityProperties;

@Service
public class SessionCookieService {

    private final String cookieName;
    private final boolean secure;
    private final Duration absoluteTimeout;

    SessionCookieService(IdentityProperties properties) {
        this.cookieName = properties.cookie().name();
        this.secure = properties.cookie().secure();
        this.absoluteTimeout = properties.session().absoluteTimeout();
    }

    public void writeSessionCookie(HttpServletRequest request, HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(absoluteTimeout.toSeconds())
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    public void clearSessionCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
