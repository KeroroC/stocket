package com.stocket.identity.internal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stocket.identity")
public record IdentityProperties(
        CookieProperties cookie,
        SessionProperties session,
        InviteProperties invite,
        LoginProperties login) {

    public record CookieProperties(String name, boolean secure) {
    }

    public record SessionProperties(Duration idleTimeout, Duration absoluteTimeout, Duration touchInterval) {
    }

    public record InviteProperties(Duration defaultExpiry) {
    }

    public record LoginProperties(Duration rateLimitWindow, int maxFailures) {
    }
}
