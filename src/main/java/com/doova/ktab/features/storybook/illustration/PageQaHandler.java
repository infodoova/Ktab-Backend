package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.PageImageStatus;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.story.SceneText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PageQaHandler implements StepHandler {

    private final VisualQa visualQa;
    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;
    private final AiCallLedger ledger;
    private final StyleReferences styles;

    @Override
    public JobStep step() {
        return JobStep.QA_PAGE;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        QaContext ctx = persistence.qaContext(job.getStorybookId(), job.getPageIndex(), job.getGeneration());
        if (ctx.status() == StorybookStatus.FAILED) {
            // Not "success": that would strand the page, because resume only revives dead jobs.
            return StepOutcome.fail("book is FAILED; this step reruns when it is resumed");
        }
        if (ctx.imageId() == null || ctx.imageStatus() != PageImageStatus.GENERATED
                || ctx.status() != StorybookStatus.ILLUSTRATING) {
            return StepOutcome.success();
        }
        var references = ReferenceAssembler.forPage(store.get(ctx.childSheetKey()), styles.get(ctx.style()),
                ctx.companionSheetKey() == null ? null : store.get(ctx.companionSheetKey()), ctx.cast());
        LlmCall<VisualQaResponse> check = visualQa.check(store.get(ctx.imageKey()), references, SceneText.withoutOutfit(ctx.sceneEn()));
        ledger.recordLlm(ctx.bookId(), job.getId(), LlmPurpose.VISUAL_QA, check);
        persistence.recordQa(ctx.bookId(), ctx.pageId(), ctx.imageId(), job.getGeneration(), check.value());
        return StepOutcome.success();
    }
}
