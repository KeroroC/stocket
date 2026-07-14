package com.stocket.notification.internal.channel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    WebPushSender(JdbcTemplate jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    @Override
    public String channelType() {
        return "WEB_PUSH";
    }

    @Override
    public SendResult send(DeliveryAttempt attempt) {
        var subscriptions = jdbc.query("""
                select id,encrypted_endpoint,key_version from push_subscription
                where household_id=? and member_id=? and enabled=true order by id limit 1
                """, (result, row) -> new Subscription(
                result.getObject("id", UUID.class), result.getString("encrypted_endpoint"),
                result.getInt("key_version")), attempt.householdId(), attempt.memberId());
        if (subscriptions.isEmpty()) return SendResult.permanent("PUSH_SUBSCRIPTION_MISSING");
        Subscription subscription = subscriptions.getFirst();
        try {
            String endpoint = cipher.decrypt(
                    new EncryptedSecret(subscription.endpoint(), subscription.keyVersion()),
                    attempt.householdId() + ":" + subscription.id() + ":WEB_PUSH_ENDPOINT");
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("TTL", "300")
                    .header("X-Stocket-Delivery-Id", attempt.deliveryKey())
                    .POST(HttpRequest.BodyPublishers.noBody())
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
        } catch (Exception exception) {
            return SendResult.retry("NETWORK_ERROR", null);
        }
    }

    private record Subscription(UUID id, String endpoint, int keyVersion) {
    }
}
