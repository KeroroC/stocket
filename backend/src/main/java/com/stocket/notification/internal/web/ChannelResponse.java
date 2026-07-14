package com.stocket.notification.internal.web;

import java.util.Map;
import java.util.UUID;

public record ChannelResponse(
        UUID id,
        String type,
        boolean enabled,
        Map<String, Object> configuration,
        boolean hasSecret,
        long version
) {
}
