package com.doova.ktab.features.storybook.metrics;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import io.micrometer.core.instrument.Metrics;

import java.math.BigDecimal;

public final class StorybookMetrics {

    private StorybookMetrics() {
    }

    public static void jobOutcome(JobStep step, StepOutcome.Type type) {
        Metrics.counter("storybook.jobs", "step", step.name(), "outcome", type.name()).increment();
    }

    public static void aiCost(String purpose, String model, BigDecimal usd) {
        Metrics.counter("storybook.ai.cost.usd", "purpose", purpose, "model", model).increment(usd.doubleValue());
    }

    public static void qaVerdict(String result) {
        Metrics.counter("storybook.qa", "result", result).increment();
    }
}
