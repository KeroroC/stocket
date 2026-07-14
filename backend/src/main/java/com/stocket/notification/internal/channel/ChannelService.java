package com.stocket.notification.internal.channel;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.notification.internal.crypto.EncryptedSecret;
import com.stocket.notification.internal.crypto.SecretCipher;
import com.stocket.notification.internal.web.ChannelResponse;

@Service
public class ChannelService {

    private static final List<String> TYPES = List.of("IN_APP", "WEB_PUSH", "SMTP", "WEBHOOK");

    private final NotificationChannelRepository channels;
    private final CurrentHouseholdProvider currentHousehold;
    private final SecretCipher cipher;
    private final PublicEndpointPolicy endpointPolicy;
    private final Map<UUID, Instant> lastTest = new ConcurrentHashMap<>();

    ChannelService(NotificationChannelRepository channels, CurrentHouseholdProvider currentHousehold,
                   SecretCipher cipher, PublicEndpointPolicy endpointPolicy) {
        this.channels = channels;
        this.currentHousehold = currentHousehold;
        this.cipher = cipher;
        this.endpointPolicy = endpointPolicy;
    }

    @Transactional(readOnly = true)
    public List<ChannelResponse> list() {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        return channels.findByHouseholdIdOrderByType(householdId).stream().map(this::response).toList();
    }

    @Transactional
    public ChannelResponse upsert(String requestedType, boolean enabled, Map<String, Object> configuration,
                                  String secret, long version) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        String type = normalizeType(requestedType);
        Map<String, Object> safeConfiguration = sanitize(type,
                configuration == null ? Map.of() : Map.copyOf(configuration));
        Instant now = Instant.now();
        NotificationChannel channel = channels.findByHouseholdIdAndType(householdId, type)
                .map(existing -> {
                    if (existing.version() != version) throw new VersionConflictException();
                    existing.update(enabled, safeConfiguration, now);
                    return existing;
                })
                .orElseGet(() -> {
                    if (version != 0) throw new VersionConflictException();
                    return new NotificationChannel(UUID.randomUUID(), householdId, type,
                            enabled, safeConfiguration, now);
                });
        if (secret != null && !secret.isBlank()) {
            if ("WEB_PUSH".equals(type)) validateWebPushPrivateKey(secret);
            EncryptedSecret encrypted = cipher.encrypt(secret, aad(channel));
            channel.changeSecret(encrypted.ciphertext(), encrypted.keyVersion(), now);
        }
        if ("WEB_PUSH".equals(type) && !channel.hasSecret()) throw new InvalidChannelException();
        return response(channels.saveAndFlush(channel));
    }

    @Transactional(readOnly = true)
    public void test(UUID channelId) {
        UUID householdId = currentHousehold.requireCurrent().householdId();
        channels.findByHouseholdIdAndId(householdId, channelId).orElseThrow(ChannelNotFoundException::new);
        Instant now = Instant.now();
        Instant previous = lastTest.putIfAbsent(channelId, now);
        if (previous != null && Duration.between(previous, now).compareTo(Duration.ofMinutes(1)) < 0) {
            throw new RateLimitedException();
        }
        if (previous != null) lastTest.put(channelId, now);
    }

    private String normalizeType(String requestedType) {
        String type = requestedType.toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new InvalidChannelException();
        return type;
    }

    private Map<String, Object> sanitize(String type, Map<String, Object> configuration) {
        return switch (type) {
            case "SMTP" -> sanitizeSmtp(configuration);
            case "WEBHOOK" -> sanitizeWebhook(configuration);
            case "WEB_PUSH" -> sanitizeWebPush(configuration);
            default -> Map.of();
        };
    }

    private Map<String, Object> sanitizeSmtp(Map<String, Object> configuration) {
        String host = string(configuration, "host");
        String tlsMode = string(configuration, "tlsMode");
        String fromAddress = string(configuration, "fromAddress");
        String username = string(configuration, "username");
        Object portValue = configuration.get("port");
        int port = portValue instanceof Number number ? number.intValue() : -1;
        if (host.isBlank() || port < 1 || port > 65535
                || !List.of("TLS", "STARTTLS").contains(tlsMode)
                || !fromAddress.contains("@")) {
            throw new InvalidChannelException();
        }
        return Map.of("host", host, "port", port, "tlsMode", tlsMode,
                "username", username, "fromAddress", fromAddress);
    }

    private Map<String, Object> sanitizeWebhook(Map<String, Object> configuration) {
        try {
            PublicEndpointPolicy.ResolvedEndpoint endpoint = endpointPolicy.resolve(string(configuration, "url"));
            return Map.of("url", endpoint.url(), "resolvedAddresses", endpoint.addresses());
        } catch (Exception exception) {
            throw new InvalidChannelException();
        }
    }

    private Map<String, Object> sanitizeWebPush(Map<String, Object> configuration) {
        String publicKey = string(configuration, "publicKey");
        String subject = string(configuration, "subject");
        try {
            if (java.util.Base64.getUrlDecoder().decode(publicKey).length != 65
                    || !(subject.startsWith("mailto:") || subject.startsWith("https://"))) {
                throw new InvalidChannelException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidChannelException();
        }
        return Map.of("publicKey", publicKey, "subject", subject);
    }

    private void validateWebPushPrivateKey(String privateKey) {
        try {
            if (java.util.Base64.getUrlDecoder().decode(privateKey).length != 32) {
                throw new InvalidChannelException();
            }
        } catch (IllegalArgumentException exception) {
            throw new InvalidChannelException();
        }
    }

    private String string(Map<String, Object> configuration, String key) {
        Object value = configuration.get(key);
        return value instanceof String text ? text.trim() : "";
    }

    private String aad(NotificationChannel channel) {
        return channel.householdId() + ":" + channel.id() + ":" + channel.type();
    }

    private ChannelResponse response(NotificationChannel channel) {
        return new ChannelResponse(channel.id(), channel.type(), channel.enabled(),
                channel.configuration(), channel.hasSecret(), channel.version());
    }

    public static final class InvalidChannelException extends RuntimeException { }
    public static final class VersionConflictException extends RuntimeException { }
    public static final class RateLimitedException extends RuntimeException { }
    public static final class ChannelNotFoundException extends RuntimeException { }
}
