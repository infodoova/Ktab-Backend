package com.doova.ktab.enums.ratelimit;

/**
 * Categorization of endpoints for rate limiting enforcement.
 */
public enum RateLimitTier {
    /**
     * Strict tier for sensitive operations (login, registration, password reset).
     */
    AUTH,

    /**
     * Conservative tier for resource-intensive or cost-incurring endpoints (AI completions, story generation).
     */
    AI,

    /**
     * Standard tier for all general application APIs.
     */
    GENERAL,

    /**
     * Exempt tier for health checks, documentation, and static resources.
     */
    SKIP
}
