package com.doova.ktab.config.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration properties for Bucket4j rate limiting.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.rate-limiting")
public class RateLimitProperties {

    /**
     * Master switch to enable or disable rate limiting globally.
     */
    private boolean enabled = true;

    /**
     * Strict tier for authentication and credential-sensitive endpoints.
     */
    private TierConfig auth = new TierConfig(10, 10, 60);

    /**
     * Tier for high-cost AI / LLM / TTS endpoints.
     */
    private TierConfig ai = new TierConfig(20, 20, 60);

    /**
     * Default tier for standard REST API endpoints.
     */
    private TierConfig general = new TierConfig(100, 100, 60);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TierConfig {
        /**
         * Maximum burst capacity of the token bucket.
         */
        private long capacity;

        /**
         * Number of tokens refilled per cycle.
         */
        private long refillTokens;

        /**
         * Duration of the refill cycle in seconds.
         */
        private long refillDurationSeconds;
    }
}
