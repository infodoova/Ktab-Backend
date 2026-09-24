package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.story.CriticReport;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.StoryCritic;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoryCriticHandler implements StepHandler {

    /** Plans per book: the first plus two fresh restarts. */
    public static final int MAX_PLANS = 3;

    private final StoryCritic critic;
    private final StoryWriter writer;
    private final StoryPersistence persistence;
    private final AiCallLedger ledger;
    private final StorybookProperties properties;

    @Override
    public JobStep step() {
        return JobStep.STORY_CRITIC;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        StoryContext ctx = persistence.load(job.getStorybookId());
        if (ctx.status() != StorybookStatus.DRAFT || ctx.storedPlan() == null) {
            return StepOutcome.success();
        }

        CriticReport report = review(ctx, ctx.storedPlan(), job);
        int rounds = 0;
        while (!report.allPass() && !report.problemsByPage().containsKey(0)
                && rounds < properties.getLimits().getCriticRewritesPerPage()) {
            StoryPlanResponse plan = report.plan();
            for (int pageNumber : report.failingPages()) {
                PagePlan current = plan.pages().get(pageNumber - 1);
                LlmCall<PagePlan> rewrite = writer.rewritePage(ctx.request(), current, report.problemsByPage().get(pageNumber));
                ledger.recordLlm(ctx.bookId(), job.getId(), LlmPurpose.STORY_PAGE_REWRITE, rewrite);
                plan = plan.withPage(rewrite.value());
            }
            report = review(ctx, plan, job);
            rounds++;
        }

        if (report.allPass()) {
            persistence.acceptStory(ctx.bookId(), report.plan());
        } else if (job.getGeneration() + 1 < MAX_PLANS) {
            log.info("storybook {} story plan {} failed checks, starting a fresh plan: {}",
                    ctx.bookId(), job.getGeneration(), report.problemsByPage());
            persistence.restartPlan(ctx.bookId(), job.getGeneration() + 1, report.problemsByPage());
        } else {
            persistence.failStory(ctx.bookId(), "Story failed quality checks: " + report.problemsByPage());
        }
        return StepOutcome.success();
    }

    private CriticReport review(StoryContext ctx, StoryPlanResponse plan, StorybookJob job) {
        CriticReport report = critic.review(ctx.request(), plan);
        ledger.recordLlm(ctx.bookId(), job.getId(), LlmPurpose.STORY_CRITIC, report.llmCall());
        return report;
    }
}
