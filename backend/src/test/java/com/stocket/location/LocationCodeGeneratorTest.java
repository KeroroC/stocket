package com.stocket.location;

import org.junit.jupiter.api.Test;

import com.stocket.location.internal.LocationCodeGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationCodeGeneratorTest {

    private final LocationCodeGenerator generator = new LocationCodeGenerator();

    @Test
    void generatesUrlSafe128BitCodeAndPayload() {
        String code = generator.generateCode();
        assertThat(code).hasSize(22).matches("[A-Za-z0-9_-]{22}");
        assertThat(generator.payload(code)).isEqualTo("stocket:location:" + code);
        assertThat(generator.parsePayload(generator.payload(code))).isEqualTo(code);
    }

    @Test
    void rejectsInvalidOrOversizedPayload() {
        assertThatThrownBy(() -> generator.parsePayload("https://example.com/location/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.parsePayload("stocket:location:" + "x".repeat(120)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
