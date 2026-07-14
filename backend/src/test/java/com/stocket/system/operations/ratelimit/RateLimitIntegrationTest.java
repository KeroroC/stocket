package com.stocket.system.operations.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitIntegrationTest {
    @Test void classifiesSensitiveRoutesAndReturnsStable429WithRetryAfter() throws Exception {
        assertThat(RateLimitPolicy.forRequest("POST", "/api/v1/auth/login")).isEqualTo(RateLimitPolicy.LOGIN);
        assertThat(RateLimitPolicy.forRequest("POST", "/api/v1/setup/initialize")).isEqualTo(RateLimitPolicy.SETUP);
        assertThat(RateLimitPolicy.forRequest("POST", "/api/v1/invites/token/accept")).isEqualTo(RateLimitPolicy.INVITE_ACCEPT);
        assertThat(RateLimitPolicy.forRequest("POST", "/api/v1/admin/members/id/reset-password")).isEqualTo(RateLimitPolicy.PASSWORD_RESET);
        assertThat(RateLimitPolicy.forRequest("POST", "/api/v1/attachments")).isEqualTo(RateLimitPolicy.UPLOAD);
        assertThat(RateLimitPolicy.forRequest("POST", "/api/v1/notification/channels/id/test")).isEqualTo(RateLimitPolicy.CHANNEL_TEST);

        Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
        RateLimitProperties properties = new RateLimitProperties();
        RateLimiter limiter = new RateLimiter(clock, "01234567890123456789012345678901".getBytes(), 1_000);
        RateLimitFilter filter = new RateLimitFilter(limiter, new ClientAddressResolver(List.of()), properties);

        for (int attempt = 1; attempt <= 6; attempt++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/setup/initialize");
            request.setRemoteAddr("198.51.100.10");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, (incoming, outgoing) -> ((MockHttpServletResponse) outgoing).setStatus(409));
            if (attempt <= 5) assertThat(response.getStatus()).isEqualTo(409);
            else {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isNotBlank();
                assertThat(response.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
            }
        }
    }
}
