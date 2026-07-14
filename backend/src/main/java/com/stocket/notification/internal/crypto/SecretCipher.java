package com.stocket.notification.internal.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final MasterKeyProvider keys;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(MasterKeyProvider keys) {
        this.keys = keys;
    }

    public EncryptedSecret encrypt(String plaintext, String associatedData) {
        keys.requireAvailable();
        int version = keys.currentVersion();
        byte[] nonce = new byte[NONCE_LENGTH];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.currentKey(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(1 + nonce.length + encrypted.length)
                    .put((byte) version).put(nonce).put(encrypted).array();
            return new EncryptedSecret(Base64.getEncoder().encodeToString(payload), version);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Secret encryption failed", exception);
        }
    }

    public String decrypt(EncryptedSecret secret, String associatedData) {
        try {
            byte[] payload = Base64.getDecoder().decode(secret.ciphertext());
            if (payload.length <= 1 + NONCE_LENGTH) throw new IllegalArgumentException("Invalid ciphertext");
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            int version = Byte.toUnsignedInt(buffer.get());
            if (version != secret.keyVersion()) throw new IllegalArgumentException("Key version mismatch");
            byte[] nonce = new byte[NONCE_LENGTH];
            buffer.get(nonce);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.key(version), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Secret decryption failed", exception);
        }
    }

    public EncryptedSecret rotate(EncryptedSecret secret, String associatedData) {
        return encrypt(decrypt(secret, associatedData), associatedData);
    }
}
