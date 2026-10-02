package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.CharacterPrompts;
import com.doova.ktab.features.storybook.story.SceneText;
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
            // the approved cover is the style anchor for every story page; it goes last so the other references keep their places
            byte[] anchor = ctx.anchorKey() == null ? null : store.get(ctx.anchorKey());
            var references = new java.util.ArrayList<>(ReferenceAssembler.forPage(store.get(ctx.childSheetKey()), styles.get(ctx.style()),
                    hasCompanion ? store.get(ctx.companionSheetKey()) : null, ctx.cast()));
            // supporting characters in this scene bring their own sheets: after the companion, before the cover
            java.util.List<CharacterPrompts.SupportingLook> looks = new java.util.ArrayList<>();
            java.util.Set<String> inScene = new java.util.HashSet<>();
            ctx.cast().forEach(c -> inScene.add(c.ref()));
            for (PageContext.SupportingLook s : ctx.supporting()) {
                byte[] sheet = inScene.contains(s.ref()) && s.sheetKey() != null ? store.get(s.sheetKey()) : null;
                if (sheet != null) {
                    references.add(new com.doova.ktab.features.storybook.image.ReferenceImage(sheet, "image/png"));
                    looks.add(new CharacterPrompts.SupportingLook(s.ref(), s.describeEn(), s.clothing()));
                }
            }
            CharacterPrompts.PageLock lock = new CharacterPrompts.PageLock(ctx.hijab(), ctx.glasses(), ctx.appearanceEn(),
                    ctx.childClothing(), ctx.companionEn(), ctx.styleNotes(), anchor != null, looks);
            String prompt = ctx.kind() == PageKind.COVER
                    ? CharacterPrompts.cover(SceneText.withoutOutfit(ctx.sceneEn()), hasCompanion, lock)
                    : CharacterPrompts.scene(SceneText.withoutOutfit(ctx.sceneEn()), ctx.textZone(), hasCompanion
                        && ctx.cast().stream().anyMatch(c -> "COMPANION".equals(c.ref())), lock);
            if (anchor != null) {
                references.add(new com.doova.ktab.features.storybook.image.ReferenceImage(anchor, "image/png"));
            }
            ImageResult result = images.generate(new ImageRequest(model, prompt, references));
            cost = ledger.recordImage(ctx.bookId(), job.getId(), "IMAGE_PAGE", result);
            store.put(key, result.bytes(), result.mimeType());
        }
        persistence.savePageImage(ctx.bookId(), ctx.pageId(), ctx.pageIndex(), generation, key, model, cost);
        return StepOutcome.success();
    }
}
