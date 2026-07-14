package com.stocket.notification.internal.web;

import java.util.Map;

public record ChannelRequest(
        boolean enabled,
        Map<String, Object> configuration,
        String secret,
        long version
) {
}
