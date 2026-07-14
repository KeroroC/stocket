package com.stocket.identity;

import java.util.Optional;

@org.springframework.modulith.NamedInterface("api")
public final class RequestContext {
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    private RequestContext() { }

    public static void begin(String requestId) { REQUEST_ID.set(requestId); }
    public static Optional<String> currentRequestId() { return Optional.ofNullable(REQUEST_ID.get()); }
    public static String requireRequestId() { return currentRequestId().orElseThrow(() -> new IllegalStateException("REQUEST_CONTEXT_MISSING")); }
    public static void clear() { REQUEST_ID.remove(); }
}
