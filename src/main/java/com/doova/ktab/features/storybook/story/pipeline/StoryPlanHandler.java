package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoryPlanHandler implements StepHandler {

    private final StoryWriter writer;
    private final StoryPersistence persistence;
    private final AiCallLedger ledger;

    @Override
    public JobStep step() {
        return JobStep.STORY_PLAN;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        StoryContext ctx = persistence.load(job.getStorybookId());
        if (ctx.status() != StorybookStatus.DRAFT) {
            return StepOutcome.success();
        }
        if (ctx.storedPlan() != null) {
            persistence.enqueueCritic(ctx.bookId(), job.getGeneration());
            return StepOutcome.success();
        }
        LlmCall<StoryPlanResponse> call = writer.writePlan(ctx.request());
        ledger.recordLlm(ctx.bookId(), job.getId(), LlmPurpose.STORY_PLAN, call);
        persistence.savePlan(ctx.bookId(), call.value(), job.getGeneration());
        return StepOutcome.success();
    }
}
