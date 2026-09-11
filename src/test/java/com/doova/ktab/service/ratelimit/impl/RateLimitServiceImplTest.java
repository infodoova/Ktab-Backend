package com.doova.ktab.service.ratelimit.impl;

import com.doova.ktab.config.ratelimit.RateLimitProperties;
import com.doova.ktab.enums.ratelimit.RateLimitTier;
import com.doova.ktab.service.ratelimit.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceImplTest {

    private RateLimitProperties properties;
    private RateLimitServiceImpl rateLimitService;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setAuth(new RateLimitProperties.TierConfig(3, 3, 60));
        properties.setAi(new RateLimitProperties.TierConfig(5, 5, 60));
        properties.setGeneral(new RateLimitProperties.TierConfig(10, 10, 60));

        rateLimitService = new RateLimitServiceImpl(properties);
    }

    @Test
    @DisplayName("tryConsume allows requests within capacity and tracks remaining tokens")
    void tryConsume_withinCapacity_returnsPermitted() {
        String clientIp = "192.168.1.50";

        RateLimitResult first = rateLimitService.tryConsume(clientIp, RateLimitTier.AUTH);
        assertThat(first.allowed()).isTrue();
        assertThat(first.limit()).isEqualTo(3L);
        assertThat(first.remainingTokens()).isEqualTo(2L);

        RateLimitResult second = rateLimitService.tryConsume(clientIp, RateLimitTier.AUTH);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remainingTokens()).isEqualTo(1L);

        RateLimitResult third = rateLimitService.tryConsume(clientIp, RateLimitTier.AUTH);
        assertThat(third.allowed()).isTrue();
        assertThat(third.remainingTokens()).isEqualTo(0L);
    }

    @Test
    @DisplayName("tryConsume rejects request and provides retry-after seconds when capacity is exhausted")
    void tryConsume_exceedingCapacity_returnsRejected() {
        String clientIp = "10.0.0.1";

        for (int i = 0; i < 3; i++) {
            rateLimitService.tryConsume(clientIp, RateLimitTier.AUTH);
        }

        RateLimitResult fourth = rateLimitService.tryConsume(clientIp, RateLimitTier.AUTH);
        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.remainingTokens()).isZero();
        assertThat(fourth.secondsToWaitForRefill()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("tryConsume skips evaluation when rate limiting is globally disabled")
    void tryConsume_whenGloballyDisabled_returnsSkip() {
        properties.setEnabled(false);
        RateLimitResult result = rateLimitService.tryConsume("127.0.0.1", RateLimitTier.AUTH);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("tryConsume skips evaluation when tier is explicitly SKIP")
    void tryConsume_whenTierIsSkip_returnsSkip() {
        RateLimitResult result = rateLimitService.tryConsume("127.0.0.1", RateLimitTier.SKIP);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(-1L);
    }

    @Test
    @DisplayName("tryConsume isolates buckets across different client IPs")
    void tryConsume_multipleClients_isolatesBuckets() {
        String clientA = "1.1.1.1";
        String clientB = "2.2.2.2";

        for (int i = 0; i < 3; i++) {
            rateLimitService.tryConsume(clientA, RateLimitTier.AUTH);
        }

        RateLimitResult resultA = rateLimitService.tryConsume(clientA, RateLimitTier.AUTH);
        RateLimitResult resultB = rateLimitService.tryConsume(clientB, RateLimitTier.AUTH);

        assertThat(resultA.allowed()).isFalse();
        assertThat(resultB.allowed()).isTrue();
    }
}
