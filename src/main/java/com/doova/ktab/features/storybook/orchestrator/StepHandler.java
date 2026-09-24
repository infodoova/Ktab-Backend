package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.StorybookJob;

/**
 * One pipeline step. Called outside any transaction. Must be idempotent: when the output for
 * this job's idempotency key already exists, return success() without calling a provider, so
 * a retry after a crash never pays twice.
 */
public interface StepHandler {
    JobStep step();

    StepOutcome handle(StorybookJob job);
}
