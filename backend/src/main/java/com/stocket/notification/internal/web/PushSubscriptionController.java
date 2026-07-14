package com.stocket.notification.internal.web;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.stocket.identity.CurrentHousehold;
import com.stocket.identity.CurrentHouseholdProvider;
import com.stocket.notification.internal.channel.PublicEndpointPolicy;
import com.stocket.notification.internal.crypto.EncryptedSecret;
import com.stocket.notification.internal.crypto.SecretCipher;

@RestController
@RequestMapping("/api/v1/notification/push-subscription")
class PushSubscriptionController {

    private final JdbcTemplate jdbc;
    private final CurrentHouseholdProvider currentHousehold;
    private final SecretCipher cipher;
    private final PublicEndpointPolicy endpointPolicy;

    PushSubscriptionController(JdbcTemplate jdbc, CurrentHouseholdProvider currentHousehold,
                               SecretCipher cipher, PublicEndpointPolicy endpointPolicy) {
        this.jdbc = jdbc;
        this.currentHousehold = currentHousehold;
        this.cipher = cipher;
        this.endpointPolicy = endpointPolicy;
    }

    @PutMapping
    @Transactional
    PushSubscriptionResponse upsert(@RequestBody PushSubscriptionRequest request) {
        CurrentHousehold current = currentHousehold.requireCurrent();
        validate(request);
        String endpointHash = sha256(request.endpoint());
        List<UUID> existing = jdbc.queryForList("""
                select id from push_subscription where member_id=? and endpoint_hash=?
                """, UUID.class, current.memberId(), endpointHash);
        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.getFirst();
        EncryptedSecret endpoint = cipher.encrypt(request.endpoint(), aad(current.householdId(), id, "ENDPOINT"));
        EncryptedSecret p256dh = cipher.encrypt(request.p256dh(), aad(current.householdId(), id, "P256DH"));
        EncryptedSecret auth = cipher.encrypt(request.auth(), aad(current.householdId(), id, "AUTH"));
        if (existing.isEmpty()) {
            jdbc.update("""
                    insert into push_subscription(id,household_id,member_id,endpoint_hash,encrypted_endpoint,
                        encrypted_p256dh,encrypted_auth,key_version,enabled,created_at,updated_at)
                    values (?,?,?,?,?,?,?,?,true,now(),now())
                    """, id, current.householdId(), current.memberId(), endpointHash,
                    endpoint.ciphertext(), p256dh.ciphertext(), auth.ciphertext(), endpoint.keyVersion());
        } else {
            jdbc.update("""
                    update push_subscription
                    set encrypted_endpoint=?,encrypted_p256dh=?,encrypted_auth=?,key_version=?,
                        enabled=true,updated_at=now()
                    where id=? and household_id=? and member_id=?
                    """, endpoint.ciphertext(), p256dh.ciphertext(), auth.ciphertext(), endpoint.keyVersion(),
                    id, current.householdId(), current.memberId());
        }
        return new PushSubscriptionResponse(id, summarize(request.endpoint()), true);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void delete() {
        CurrentHousehold current = currentHousehold.requireCurrent();
        jdbc.update("""
                update push_subscription set enabled=false,updated_at=now()
                where household_id=? and member_id=? and enabled=true
                """, current.householdId(), current.memberId());
    }

    private void validate(PushSubscriptionRequest request) {
        try {
            URI uri = URI.create(request.endpoint());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || request.p256dh() == null || request.p256dh().isBlank()
                    || request.auth() == null || request.auth().isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
            }
            endpointPolicy.requirePublic(request.endpoint());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String summarize(String endpoint) {
        URI uri = URI.create(endpoint);
        return uri.getScheme() + "://" + uri.getHost() + "/…";
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String aad(UUID householdId, UUID subscriptionId, String field) {
        return householdId + ":" + subscriptionId + ":WEB_PUSH_" + field;
    }

    record PushSubscriptionRequest(String endpoint, String p256dh, String auth) {
    }

    record PushSubscriptionResponse(UUID id, String endpointSummary, boolean enabled) {
    }
}
