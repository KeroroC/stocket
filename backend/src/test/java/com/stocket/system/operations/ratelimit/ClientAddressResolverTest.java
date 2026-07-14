package com.stocket.system.operations.ratelimit;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressResolverTest {
    private final ClientAddressResolver resolver = new ClientAddressResolver(List.of("10.0.0.0/8", "fd00::/8"));

    @Test void trustsOneForwardedAddressOnlyFromConfiguredProxy() {
        MockHttpServletRequest trusted = new MockHttpServletRequest();
        trusted.setRemoteAddr("10.1.2.3"); trusted.addHeader("X-Forwarded-For", "203.0.113.9");
        assertThat(resolver.resolve(trusted)).isEqualTo("203.0.113.9");

        MockHttpServletRequest spoofed = new MockHttpServletRequest();
        spoofed.setRemoteAddr("198.51.100.7"); spoofed.addHeader("X-Forwarded-For", "203.0.113.9");
        assertThat(resolver.resolve(spoofed)).isEqualTo("198.51.100.7");

        MockHttpServletRequest chain = new MockHttpServletRequest();
        chain.setRemoteAddr("10.1.2.3"); chain.addHeader("X-Forwarded-For", "203.0.113.9, 198.51.100.8");
        assertThat(resolver.resolve(chain)).isEqualTo("10.1.2.3");
    }
}
