package com.stocket.system.operations.ratelimit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("stocket.rate-limit")
public class RateLimitProperties {
    private int maxKeys = 50_000;
    private Map<String, Rule> policies = new HashMap<>();

    public int getMaxKeys() { return maxKeys; }
    public void setMaxKeys(int maxKeys) { this.maxKeys = Math.clamp(maxKeys, 1_000, 100_000); }
    public Map<String, Rule> getPolicies() { return policies; }
    public void setPolicies(Map<String, Rule> policies) { this.policies = policies == null ? new HashMap<>() : policies; }

    public Limit limit(RateLimitPolicy policy) {
        Rule configured = policies.get(policy.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        if (configured == null) return new Limit(policy.capacity(), policy.window());
        int capacity = Math.max(1, Math.min(configured.capacity, policy.capacity()));
        Duration window = configured.window == null || configured.window.compareTo(policy.window()) < 0
                ? policy.window() : configured.window;
        return new Limit(capacity, window);
    }

    public record Limit(int capacity, Duration window) { }

    public static class Rule {
        private int capacity;
        private Duration window;
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
    }
}
