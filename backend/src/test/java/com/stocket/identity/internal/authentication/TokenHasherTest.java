package com.stocket.identity.internal.authentication;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private final TokenHasher hasher = new TokenHasher();

    @Test
    void sha256ProducesCorrectHash() {
        assertThat(hasher.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Produces64CharacterHex() {
        String hash = hasher.sha256("test");
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }
}
