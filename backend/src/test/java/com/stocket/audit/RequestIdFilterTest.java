package com.stocket.audit;

import java.util.concurrent.atomic.AtomicReference;

import com.stocket.identity.RequestContext;
import com.stocket.system.internal.tracing.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    @Test void acceptsValidRequestIdAndAlwaysClearsThreadContext() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system");
        request.addHeader("X-Request-Id", "client.request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            seen.set(RequestContext.requireRequestId());
            assertThat(MDC.get("requestId")).isEqualTo("client.request-123");
        });

        assertThat(seen).hasValue("client.request-123");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("client.request-123");
        assertThat(RequestContext.currentRequestId()).isEmpty();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void replacesInvalidOrOversizedRequestIds() throws Exception {
        for (String invalid : new String[]{"short", "contains space", "x".repeat(81)}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
            request.addHeader("X-Request-Id", invalid);
            MockHttpServletResponse response = new MockHttpServletResponse();
            new RequestIdFilter().doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                    assertThat(RequestContext.requireRequestId()).matches("[0-9a-f-]{36}"));
            assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
        }
    }
}
