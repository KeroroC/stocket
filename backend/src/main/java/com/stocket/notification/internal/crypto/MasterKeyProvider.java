package com.stocket.notification.internal.crypto;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MasterKeyProvider implements HealthIndicator {

    private final int currentVersion;
    private final Map<Integer, SecretKey> keys;
    private final boolean valid;

    public MasterKeyProvider(
            @Value("${STOCKET_MASTER_KEY:}") String currentKey,
            @Value("${STOCKET_MASTER_KEY_VERSION:1}") int currentVersion,
            @Value("${STOCKET_PREVIOUS_MASTER_KEY:}") String previousKey,
            @Value("${STOCKET_PREVIOUS_MASTER_KEY_VERSION:#{null}}") Integer previousVersion) {
        this.currentVersion = currentVersion;
        Map<Integer, SecretKey> decoded = new HashMap<>();
        boolean currentValid = add(decoded, currentVersion, currentKey, true);
        boolean previousValid = add(decoded, previousVersion, previousKey, false);
        this.keys = Map.copyOf(decoded);
        this.valid = currentValid && previousValid && currentVersion > 0 && currentVersion <= 255;
    }

    public int currentVersion() {
        requireAvailable();
        return currentVersion;
    }

    public SecretKey currentKey() {
        return key(currentVersion);
    }

    public SecretKey key(int version) {
        requireAvailable();
        SecretKey key = keys.get(version);
        if (key == null) {
            throw new IllegalArgumentException("Unknown master key version");
        }
        return key;
    }

    public void requireAvailable() {
        if (!valid) {
            throw new IllegalStateException("STOCKET_MASTER_KEY must be 32-byte Base64");
        }
    }

    @Override
    public Health health() {
        return valid
                ? Health.up().withDetail("keyVersion", currentVersion).build()
                : Health.down().withDetail("reason", "master key unavailable").build();
    }

    private boolean add(Map<Integer, SecretKey> target, Integer version, String encoded, boolean required) {
        if (encoded == null || encoded.isBlank()) {
            return !required;
        }
        if (version == null || version < 1 || version > 255) {
            return false;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length != 32) return false;
            target.put(version, new SecretKeySpec(bytes, "AES"));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
