package com.microcare.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiting configuration.
 *
 * <p>The gateway uses Spring Cloud Gateway's {@code RequestRateLimiter} filter
 * backed by {@code RedisRateLimiter} (see {@code spring.cloud.gateway.default-filters}
 * in {@code application.yml}). Requests are keyed by client IP so each caller
 * gets an independent token bucket.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Keys the rate limiter by the client IP — honoring the first value of the
     * {@code X-Forwarded-For} header when present (reverse-proxy deployments)
     * and falling back to the direct remote address.
     */
    @Bean
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return Mono.just(forwarded.split(",")[0].trim());
            }
            return Mono.just(exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown");
        };
    }
}
