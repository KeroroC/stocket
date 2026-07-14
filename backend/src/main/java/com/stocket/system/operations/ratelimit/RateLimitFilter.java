package com.stocket.system.operations.ratelimit;

import java.io.IOException;

import com.stocket.identity.CurrentHouseholdProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@Component
@ConditionalOnBean(RateLimiter.class)
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class RateLimitFilter extends OncePerRequestFilter {
    private final RateLimiter limiter;
    private final ClientAddressResolver addresses;
    private final RateLimitProperties properties;
    private final ObjectProvider<CurrentHouseholdProvider> current;

    @Autowired
    public RateLimitFilter(RateLimiter limiter, ClientAddressResolver addresses, RateLimitProperties properties,
                           ObjectProvider<CurrentHouseholdProvider> current) {
        this.limiter = limiter; this.addresses = addresses; this.properties = properties; this.current = current;
    }

    RateLimitFilter(RateLimiter limiter, ClientAddressResolver addresses, RateLimitProperties properties) {
        this(limiter, addresses, properties, null);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimitPolicy selected = RateLimitPolicy.forRequest(request.getMethod(), request.getRequestURI());
        RateLimitPolicy enforced = selected.domainManaged() ? RateLimitPolicy.GENERAL : selected;
        String rawKey = enforced.name() + '|' + principal() + '|' + addresses.resolve(request);
        RateLimiter.Decision decision = limiter.acquire(rawKey, properties.limit(enforced));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            if (selected == RateLimitPolicy.SETUP && response.getStatus() < 400) limiter.clear(rawKey);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(Math.max(1, decision.retryAfter().toSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"Too many requests\",\"status\":429,\"code\":\"RATE_LIMITED\",\"retryable\":true}");
    }

    private String principal() {
        if (current == null) return "anonymous";
        CurrentHouseholdProvider provider = current.getIfAvailable();
        if (provider == null) return "anonymous";
        try { return provider.requireCurrent().accountId().toString(); }
        catch (RuntimeException error) { return "anonymous"; }
    }
}
