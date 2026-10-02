package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RenderPdfHandler implements StepHandler {

    private final PlaywrightPdfRenderer renderer;
    private final StorybookAssetStore store;
    private final RenderPersistence persistence;

    @Override
    public JobStep step() {
        return JobStep.RENDER_PDF;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        RenderContext ctx = persistence.context(job.getStorybookId());
        if (ctx.status() != StorybookStatus.RENDERING) {
            return StepOutcome.success();
        }
        String key = StorybookKeys.pdf(ctx.bookId(), job.getGeneration());
        if (!store.exists(key)) {
            // PageKind is COVER/STORY only (enums/PageKind.java); the switch is exhaustive on purpose so
            // that adding a third kind that RenderModelFactory embeds an image for forces a decision here
            // at compile time instead of quietly falling through a stale "not COVER/STORY, skip" guard.
            Map<Integer, byte[]> images = new HashMap<>();
            for (RenderModelFactory.PageSource p : ctx.pages()) {
                boolean needsImage = switch (p.kind()) {
                    case COVER, STORY -> true;
                };
                if (!needsImage) {
                    continue;
                }
                String imageKey = ctx.imageKeysByPageIndex().get(p.pageIndex());
                if (imageKey == null) {
                    // IllustrationPersistence#advance only reaches RENDERING once every cover/story page has
                    // a QA-passed image; a missing key here means that invariant broke. Fail the job outright
                    // (not a retry: retrying an unchanged data-integrity gap just wastes the retry budget)
                    // instead of silently shipping a paying customer a book with a blank page.
                    return StepOutcome.fail("Storybook " + ctx.bookId() + " page " + p.pageIndex()
                            + " (" + p.kind() + ") has no image; refusing to render an incomplete PDF");
                }
                images.put(p.pageIndex(), store.get(imageKey));
            }
            BookRenderModel model = RenderModelFactory.build(ctx.titleAr(), ctx.childNameAr(), ctx.dedication(),
                    ctx.level(), ctx.pages());
            store.put(key, renderer.render(model, images), "application/pdf");
        }
        persistence.finish(ctx.bookId(), key);
        return StepOutcome.success();
    }
}
