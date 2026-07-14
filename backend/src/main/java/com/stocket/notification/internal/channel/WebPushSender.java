package com.stocket.notification.internal.channel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.stocket.notification.internal.crypto.EncryptedSecret;
import com.stocket.notification.internal.crypto.SecretCipher;
import com.stocket.notification.internal.worker.ChannelSender;
import com.stocket.notification.internal.worker.DeliveryAttempt;
import com.stocket.notification.internal.worker.SendResult;

@Component
public class WebPushSender implements ChannelSender {

    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final NotificationChannelRepository channels;
    private final WebPushMessageEncoder encoder;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    WebPushSender(JdbcTemplate jdbc, SecretCipher cipher, NotificationChannelRepository channels,
                  WebPushMessageEncoder encoder) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.channels = channels;
        this.encoder = encoder;
    }

    @Override
    public String channelType() {
        return "WEB_PUSH";
    }

    @Override
    public SendResult send(DeliveryAttempt attempt) {
        var subscriptions = jdbc.query("""
                select id,encrypted_endpoint,encrypted_p256dh,encrypted_auth,key_version from push_subscription
                where household_id=? and member_id=? and enabled=true order by id limit 1
                """, (result, row) -> new Subscription(
                result.getObject("id", UUID.class), result.getString("encrypted_endpoint"),
                result.getString("encrypted_p256dh"), result.getString("encrypted_auth"),
                result.getInt("key_version")), attempt.householdId(), attempt.memberId());
        if (subscriptions.isEmpty()) return SendResult.permanent("PUSH_SUBSCRIPTION_MISSING");
        Subscription subscription = subscriptions.getFirst();
        try {
            NotificationChannel channel = channels.findByHouseholdIdAndId(
                    attempt.householdId(), attempt.channelId()).orElse(null);
            if (channel == null || !channel.enabled() || !channel.hasSecret()) {
                return SendResult.permanent("PUSH_CHANNEL_INVALID");
            }
            String endpoint = decrypt(subscription.endpoint(), subscription, attempt, "ENDPOINT");
            String p256dh = decrypt(subscription.p256dh(), subscription, attempt, "P256DH");
            String auth = decrypt(subscription.auth(), subscription, attempt, "AUTH");
            String privateKey = cipher.decrypt(
                    new EncryptedSecret(channel.encryptedSecret(), channel.keyVersion()),
                    channel.householdId() + ":" + channel.id() + ":" + channel.type());
            String publicKey = string(channel, "publicKey");
            String subject = string(channel, "subject");
            String payload = "{\"deliveryId\":\"" + attempt.deliveryKey() + "\",\"reminderId\":\""
                    + attempt.reminderId() + "\"}";
            WebPushMessageEncoder.EncodedPush encoded = encoder.encode(endpoint, p256dh, auth,
                    payload.getBytes(java.nio.charset.StandardCharsets.UTF_8), publicKey, privateKey,
                    subject, Instant.now());
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", "300")
                    .header("X-Stocket-Delivery-Id", attempt.deliveryKey())
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Encoding", encoded.contentEncoding())
                    .header("Authorization", encoded.authorization())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(encoded.body()))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 404 || response.statusCode() == 410) {
                jdbc.update("update push_subscription set enabled=false,updated_at=now() where id=?",
                        subscription.id());
                return SendResult.permanent("PUSH_SUBSCRIPTION_GONE");
            }
            return SendResult.fromHttp(response.statusCode(), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return SendResult.retry("NETWORK_INTERRUPTED", null);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            jdbc.update("update push_subscription set enabled=false,updated_at=now() where id=?",
                    subscription.id());
            return SendResult.permanent("PUSH_SUBSCRIPTION_INVALID");
        } catch (Exception exception) {
            return SendResult.retry("NETWORK_ERROR", null);
        }
    }

    private String decrypt(String ciphertext, Subscription subscription, DeliveryAttempt attempt, String field) {
        return cipher.decrypt(new EncryptedSecret(ciphertext, subscription.keyVersion()),
                attempt.householdId() + ":" + subscription.id() + ":WEB_PUSH_" + field);
    }

    private String string(NotificationChannel channel, String key) {
        Object value = channel.configuration().get(key);
        return value instanceof String text ? text : "";
    }

    private record Subscription(UUID id, String endpoint, String p256dh, String auth, int keyVersion) {
    }
}
