package com.stocket.identity.internal.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderConfigurationTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfiguration().passwordEncoder();

    @Test
    void encodedResultDiffersFromPlaintext() {
        String encoded = encoder.encode("password");
        assertThat(encoded).isNotEqualTo("password");
    }

    @Test
    void twoEncodingsProduceDifferentResults() {
        String first = encoder.encode("password");
        String second = encoder.encode("password");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void bothEncodingsMatchOriginalPlaintext() {
        String first = encoder.encode("password");
        String second = encoder.encode("password");
        assertThat(encoder.matches("password", first)).isTrue();
        assertThat(encoder.matches("password", second)).isTrue();
    }
}
