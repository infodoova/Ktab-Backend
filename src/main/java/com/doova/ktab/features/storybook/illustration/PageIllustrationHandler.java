package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.CharacterPrompts;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.image.ImageProvider;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class PageIllustrationHandler implements StepHandler {

    private final ImageProvider images;
    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;
    private final AiCallLedger ledger;
    private final StyleReferences styles;
    private final ModelSelector models;

    @Override
    public JobStep step() {
        return JobStep.ILLUSTRATE_PAGE;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        int generation = job.getGeneration();
        PageContext ctx = persistence.pageContext(job.getStorybookId(), job.getPageIndex(), generation);
        if (ctx.status() != StorybookStatus.ILLUSTRATING || ctx.pageGeneration() != generation) {
            return StepOutcome.success();
        }
        if (ctx.imageRowExists()) {
            persistence.ensureQaEnqueued(ctx.bookId(), ctx.pageIndex(), generation);
            return StepOutcome.success();
        }

        String model = models.modelFor(generation - ctx.roundStartGeneration() + 1);
        String key = StorybookKeys.pageImage(ctx.bookId(), ctx.pageIndex(), generation);
        BigDecimal cost = BigDecimal.ZERO;
        if (!store.exists(key)) {
            boolean hasCompanion = ctx.companionSheetKey() != null;
            String prompt = ctx.kind() == PageKind.COVER
                    ? CharacterPrompts.cover(ctx.sceneEn(), hasCompanion, ctx.hijab())
                    : CharacterPrompts.scene(ctx.sceneEn(), ctx.textZone(), hasCompanion
                        && ctx.cast().stream().anyMatch(c -> "COMPANION".equals(c.ref())), ctx.hijab());
            var references = ReferenceAssembler.forPage(store.get(ctx.childSheetKey()), styles.get(ctx.style()),
                    hasCompanion ? store.get(ctx.companionSheetKey()) : null, ctx.cast());
            ImageResult result = images.generate(new ImageRequest(model, prompt, references));
            cost = ledger.recordImage(ctx.bookId(), job.getId(), "IMAGE_PAGE", result);
            store.put(key, result.bytes(), result.mimeType());
        }
        persistence.savePageImage(ctx.bookId(), ctx.pageId(), ctx.pageIndex(), generation, key, model, cost);
        return StepOutcome.success();
    }
}
