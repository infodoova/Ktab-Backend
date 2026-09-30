package com.doova.ktab.features.storybook.metrics;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StorybookMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(registry);
    }

    @Test
    void countsJobsCostAndQa() {
        Metrics.addRegistry(registry);

        StorybookMetrics.jobOutcome(JobStep.ILLUSTRATE_PAGE, StepOutcome.Type.RETRY);
        StorybookMetrics.aiCost("IMAGE_PAGE", "gemini-3.1-flash-image", new BigDecimal("0.101"));
        StorybookMetrics.qaVerdict("flagged");

        assertThat(registry.get("storybook.jobs").tag("step", "ILLUSTRATE_PAGE").tag("outcome", "RETRY").counter().count()).isEqualTo(1);
        assertThat(registry.get("storybook.ai.cost.usd").tag("purpose", "IMAGE_PAGE").counter().count()).isEqualTo(0.101);
        assertThat(registry.get("storybook.qa").tag("result", "flagged").counter().count()).isEqualTo(1);
    }
}
