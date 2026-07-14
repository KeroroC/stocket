package com.stocket.notification;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import com.stocket.notification.internal.crypto.EncryptedSecret;
import com.stocket.notification.internal.crypto.MasterKeyProvider;
import com.stocket.notification.internal.crypto.SecretCipher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY_ONE = key("0123456789abcdef0123456789abcdef");
    private static final String KEY_TWO = key("abcdef0123456789abcdef0123456789");

    @Test
    void encryptsWithRandomNonceAndRejectsTamperingOrDifferentAssociatedData() {
        SecretCipher cipher = new SecretCipher(new MasterKeyProvider(KEY_ONE, 1, null, null));

        EncryptedSecret first = cipher.encrypt("smtp-password", "household:channel:SMTP");
        EncryptedSecret second = cipher.encrypt("smtp-password", "household:channel:SMTP");

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(cipher.decrypt(first, "household:channel:SMTP")).isEqualTo("smtp-password");
        assertThatThrownBy(() -> cipher.decrypt(first, "other:channel:SMTP"))
                .isInstanceOf(IllegalArgumentException.class);

        byte[] tampered = Base64.getDecoder().decode(first.ciphertext());
        tampered[tampered.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(
                new EncryptedSecret(Base64.getEncoder().encodeToString(tampered), first.keyVersion()),
                "household:channel:SMTP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decryptsPreviousKeyVersionAndReencryptsWithCurrentVersion() {
        EncryptedSecret old = new SecretCipher(new MasterKeyProvider(KEY_ONE, 1, null, null))
                .encrypt("webhook-secret", "household:channel:WEBHOOK");
        SecretCipher rotating = new SecretCipher(new MasterKeyProvider(KEY_TWO, 2, KEY_ONE, 1));

        assertThat(rotating.decrypt(old, "household:channel:WEBHOOK")).isEqualTo("webhook-secret");
        EncryptedSecret rotated = rotating.rotate(old, "household:channel:WEBHOOK");
        assertThat(rotated.keyVersion()).isEqualTo(2);
        assertThat(rotating.decrypt(rotated, "household:channel:WEBHOOK")).isEqualTo("webhook-secret");
    }

    @Test
    void reportsDownWhenMasterKeyIsMissingOrInvalid() {
        assertThat(new MasterKeyProvider("", 1, null, null).health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(new MasterKeyProvider("not-base64", 1, null, null).health().getStatus())
                .isEqualTo(Status.DOWN);
    }

    private static String key(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
