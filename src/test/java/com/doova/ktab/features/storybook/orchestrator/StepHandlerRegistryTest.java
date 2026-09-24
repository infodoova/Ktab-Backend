package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.StorybookJob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepHandlerRegistryTest {

    private static StepHandler handler(JobStep step) {
        return new StepHandler() {
            public JobStep step() { return step; }
            public StepOutcome handle(StorybookJob job) { return StepOutcome.success(); }
        };
    }

    @Test
    void findsTheHandlerForAStep() {
        StepHandler plan = handler(JobStep.STORY_PLAN);
        assertThat(new StepHandlerRegistry(List.of(plan)).get(JobStep.STORY_PLAN)).isSameAs(plan);
        assertThat(new StepHandlerRegistry(List.of(plan)).get(JobStep.RENDER_PDF)).isNull();
    }

    @Test
    void duplicateHandlersFailStartup() {
        assertThatThrownBy(() -> new StepHandlerRegistry(List.of(handler(JobStep.STORY_PLAN), handler(JobStep.STORY_PLAN))))
                .isInstanceOf(IllegalStateException.class);
    }
}
