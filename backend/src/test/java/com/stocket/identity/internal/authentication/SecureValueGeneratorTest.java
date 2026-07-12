package com.stocket.identity.internal.authentication;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureValueGeneratorTest {

    private final SecureValueGenerator generator = new SecureValueGenerator();

    @Test
    void generateTokenProducesUrlSafeBase64WithoutPadding() {
        String token = generator.generateToken();
        assertThat(token).doesNotContain("+", "/", "=");
        // 32 bytes → 43 characters in URL-safe base64 without padding
        assertThat(token).hasSize(43);
        // Should be valid base64 url
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void generateTokenIsRandom() {
        String first = generator.generateToken();
        String second = generator.generateToken();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generateTemporaryPasswordHasCorrectLength() {
        String password = generator.generateTemporaryPassword();
        assertThat(password).hasSize(20);
    }

    @Test
    void generateTemporaryPasswordIsRandom() {
        String first = generator.generateTemporaryPassword();
        String second = generator.generateTemporaryPassword();
        assertThat(first).isNotEqualTo(second);
    }
}
