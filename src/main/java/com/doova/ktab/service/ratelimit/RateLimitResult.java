package com.doova.ktab.service.ratelimit;

/**
 * Encapsulates the evaluation outcome of a rate-limit probe.
 *
 * @param allowed                true if the consumption succeeded and request should proceed
 * @param limit                  total bucket capacity for this tier
 * @param remainingTokens        tokens currently remaining in the bucket
 * @param secondsToWaitForRefill seconds client must wait for bucket refill when throttled
 */
public record RateLimitResult(
        boolean allowed,
        long limit,
        long remainingTokens,
        long secondsToWaitForRefill
) {
    public static RateLimitResult permitted(long limit, long remainingTokens) {
        return new RateLimitResult(true, limit, remainingTokens, 0L);
    }

    public static RateLimitResult rejected(long limit, long secondsToWaitForRefill) {
        return new RateLimitResult(false, limit, 0L, Math.max(1L, secondsToWaitForRefill));
    }

    public static RateLimitResult skip() {
        return new RateLimitResult(true, -1L, -1L, 0L);
    }
}
