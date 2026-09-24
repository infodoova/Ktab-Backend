package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffPolicyTest {

    private final StorybookProperties props = new StorybookProperties(); // base 10s

    @Test
    void growsExponentiallyWithJitterBounds() {
        BackoffPolicy low = new BackoffPolicy(props, () -> 0.0);
        BackoffPolicy high = new BackoffPolicy(props, () -> 1.0);

        assertThat(low.delayAfter(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(high.delayAfter(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(low.delayAfter(3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(high.delayAfter(3)).isEqualTo(Duration.ofSeconds(40));
    }

    @Test
    void isCappedAtThirtyMinutes() {
        assertThat(new BackoffPolicy(props, () -> 1.0).delayAfter(40)).isEqualTo(Duration.ofMinutes(30));
    }
}
