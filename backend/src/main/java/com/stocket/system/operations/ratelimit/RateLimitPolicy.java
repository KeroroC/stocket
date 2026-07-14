package com.stocket.system.operations.ratelimit;

import java.time.Duration;

public enum RateLimitPolicy {
    LOGIN(10, Duration.ofMinutes(15), true),
    SETUP(5, Duration.ofHours(1), false),
    INVITE_ACCEPT(20, Duration.ofHours(1), true),
    PASSWORD_RESET(5, Duration.ofHours(1), true),
    UPLOAD(30, Duration.ofMinutes(1), false),
    CHANNEL_TEST(5, Duration.ofMinutes(15), false),
    GENERAL(300, Duration.ofMinutes(1), false);

    private final int capacity;
    private final Duration window;
    private final boolean domainManaged;

    RateLimitPolicy(int capacity, Duration window, boolean domainManaged) {
        this.capacity = capacity; this.window = window; this.domainManaged = domainManaged;
    }

    int capacity() { return capacity; }
    Duration window() { return window; }
    boolean domainManaged() { return domainManaged; }

    public static RateLimitPolicy forRequest(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) return GENERAL;
        if ("/api/v1/auth/login".equals(path)) return LOGIN;
        if ("/api/v1/setup/initialize".equals(path)) return SETUP;
        if (path.matches("/api/v1/invites/[^/]+/accept")) return INVITE_ACCEPT;
        if (path.matches("/api/v1/admin/members/[^/]+/reset-password")) return PASSWORD_RESET;
        if ("/api/v1/attachments".equals(path)) return UPLOAD;
        if (path.matches("/api/v1/notification/channels/[^/]+/test")) return CHANNEL_TEST;
        return GENERAL;
    }
}
