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
            Map<Integer, byte[]> images = new HashMap<>();
            ctx.imageKeysByPageIndex().forEach((index, imageKey) -> images.put(index, store.get(imageKey)));
            BookRenderModel model = RenderModelFactory.build(ctx.titleAr(), ctx.childNameAr(), ctx.dedication(),
                    ctx.level(), ctx.pages());
            store.put(key, renderer.render(model, images), "application/pdf");
        }
        persistence.finish(ctx.bookId(), key);
        return StepOutcome.success();
    }
}
