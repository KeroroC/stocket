package com.stocket.notification.internal.crypto;

public record EncryptedSecret(String ciphertext, int keyVersion) {
}
