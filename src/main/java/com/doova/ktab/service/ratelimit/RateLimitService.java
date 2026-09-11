package com.doova.ktab.service.ratelimit;

import com.doova.ktab.enums.ratelimit.RateLimitTier;

/**
 * Service contract for evaluating token-bucket rate limits across endpoint tiers.
 */
public interface RateLimitService {

    /**
     * Attempts to consume a token for the given client identifier and tier.
     *
     * @param clientKey the unique client identifier (typically client IP address)
     * @param tier      the rate-limit policy tier to apply
     * @return outcome containing allow/reject decision and quota metadata
     */
    RateLimitResult tryConsume(String clientKey, RateLimitTier tier);

    /**
     * Indicates whether rate limiting is currently enabled in configuration.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
}
