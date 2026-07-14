package com.stocket.notification.internal.channel;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.stocket.notification.internal.crypto.EncryptedSecret;
import com.stocket.notification.internal.crypto.SecretCipher;
import com.stocket.notification.internal.worker.ChannelSender;
import com.stocket.notification.internal.worker.DeliveryAttempt;
import com.stocket.notification.internal.worker.SendResult;

@Component
public class WebhookSender implements ChannelSender {

    private final NotificationChannelRepository channels;
    private final SecretCipher cipher;
    private final PublicEndpointPolicy endpointPolicy;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    WebhookSender(NotificationChannelRepository channels, SecretCipher cipher,
                  PublicEndpointPolicy endpointPolicy) {
        this.channels = channels;
        this.cipher = cipher;
        this.endpointPolicy = endpointPolicy;
    }

    @Override
    public String channelType() {
        return "WEBHOOK";
    }

    @Override
    public SendResult send(DeliveryAttempt attempt) {
        NotificationChannel channel = channels.findByHouseholdIdAndId(
                attempt.householdId(), attempt.channelId()).orElse(null);
        if (channel == null || !channel.enabled()) return SendResult.permanent("CHANNEL_DISABLED");
        String url = String.valueOf(channel.configuration().getOrDefault("url", ""));
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        String body = "{\"deliveryId\":\"" + attempt.deliveryKey() + "\",\"reminderId\":\""
                + attempt.reminderId() + "\"}";
        try {
            endpointPolicy.requireStable(url, channel.configuration().get("resolvedAddresses"));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("X-Stocket-Delivery-Id", attempt.deliveryKey())
                    .header("X-Stocket-Timestamp", timestamp)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (channel.hasSecret()) {
                String secret = cipher.decrypt(
                        new EncryptedSecret(channel.encryptedSecret(), channel.keyVersion()), aad(channel));
                request.header("X-Stocket-Signature", signature(secret, timestamp + "." + body));
            }
            HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                input.readNBytes(4097);
            }
            return SendResult.fromHttp(response.statusCode(), retryAfter(response));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SendResult.retry("NETWORK_INTERRUPTED", null);
        } catch (Exception exception) {
            return SendResult.retry("NETWORK_ERROR", null);
        }
    }

    private Duration retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                return java.util.Optional.of(Duration.ofSeconds(Long.parseLong(value)));
            } catch (NumberFormatException exception) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
    }

    private String signature(String secret, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String aad(NotificationChannel channel) {
        return channel.householdId() + ":" + channel.id() + ":" + channel.type();
    }
}
