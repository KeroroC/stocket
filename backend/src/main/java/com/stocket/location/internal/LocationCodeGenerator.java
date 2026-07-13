package com.stocket.location.internal;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

@Component
public class LocationCodeGenerator {

    private static final String PREFIX = "stocket:location:";
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String payload(String code) {
        return PREFIX + code;
    }

    public String parsePayload(String payload) {
        if (payload == null || payload.length() > 128 || !payload.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Invalid location payload");
        }
        String code = payload.substring(PREFIX.length());
        if (code.isBlank() || code.length() > 64) {
            throw new IllegalArgumentException("Invalid location payload");
        }
        return code;
    }
}
