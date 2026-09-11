package com.doova.ktab.service.ratelimit.impl;

import com.doova.ktab.config.ratelimit.RateLimitProperties;
import com.doova.ktab.enums.ratelimit.RateLimitTier;
import com.doova.ktab.service.ratelimit.RateLimitResult;
import com.doova.ktab.service.ratelimit.RateLimitService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * High-performance, in-memory token bucket rate limiting implementation backed by Bucket4j.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    private static final int MAX_CACHE_ENTRIES = 25_000;

    private final RateLimitProperties properties;
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public RateLimitResult tryConsume(String clientKey, RateLimitTier tier) {
        if (!properties.isEnabled() || tier == RateLimitTier.SKIP) {
            return RateLimitResult.skip();
        }

        RateLimitProperties.TierConfig tierConfig = resolveTierConfig(tier);
        String cacheKey = tier.name() + ":" + clientKey;

        // Bounded cache safety against memory exhaustion attacks
        if (bucketCache.size() > MAX_CACHE_ENTRIES) {
            log.warn("Rate limit cache size exceeded limit of {}; clearing cache", MAX_CACHE_ENTRIES);
            bucketCache.clear();
        }

        Bucket bucket = bucketCache.computeIfAbsent(cacheKey, k -> createBucket(tierConfig));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return RateLimitResult.permitted(tierConfig.getCapacity(), probe.getRemainingTokens());
        }

        long waitNanos = probe.getNanosToWaitForRefill();
        long waitSeconds = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(waitNanos));
        log.warn("Rate limit exceeded for key '{}' on tier '{}'. Throttling for {}s", clientKey, tier, waitSeconds);

        return RateLimitResult.rejected(tierConfig.getCapacity(), waitSeconds);
    }

    private RateLimitProperties.TierConfig resolveTierConfig(RateLimitTier tier) {
        return switch (tier) {
            case AUTH -> properties.getAuth();
            case AI -> properties.getAi();
            case GENERAL, SKIP -> properties.getGeneral();
        };
    }

    private Bucket createBucket(RateLimitProperties.TierConfig config) {
        Refill refill = Refill.intervally(
                config.getRefillTokens(),
                Duration.ofSeconds(config.getRefillDurationSeconds())
        );

        Bandwidth limit = Bandwidth.classic(config.getCapacity(), refill);

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
