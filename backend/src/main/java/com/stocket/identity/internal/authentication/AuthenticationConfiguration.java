package com.stocket.identity.internal.authentication;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stocket.identity.internal.config.IdentityProperties;

@Configuration
class AuthenticationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BoundedRateLimiter<LoginThrottleKey> loginRateLimiter(Clock clock, IdentityProperties properties) {
        Duration window = properties.login().rateLimitWindow();
        int maxFailures = properties.login().maxFailures();
        return new BoundedRateLimiter<>(clock, window, maxFailures, 10000);
    }
}
