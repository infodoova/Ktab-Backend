package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

@Component
public class BackoffPolicy {

    private static final Duration CAP = Duration.ofMinutes(30);

    private final Duration base;
    private final DoubleSupplier random;

    @Autowired
    public BackoffPolicy(StorybookProperties properties) {
        this(properties, () -> ThreadLocalRandom.current().nextDouble());
    }

    public BackoffPolicy(StorybookProperties properties, DoubleSupplier random) {
        this.base = properties.getWorker().getBaseBackoff();
        this.random = random;
    }

    public Duration delayAfter(int attempts) {
        int exponent = Math.min(Math.max(attempts, 1) - 1, 20);
        long millis = Math.min(base.toMillis() * (1L << exponent), CAP.toMillis());
        long half = millis / 2;
        return Duration.ofMillis(half + Math.round(half * random.getAsDouble()));
    }
}
