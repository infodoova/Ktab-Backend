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
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.image.ImageDownscaler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class PageIllustrationHandler implements StepHandler {

    private final ImageProvider images;
    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;
    private final AiCallLedger ledger;
    private final StyleReferences styles;
    private final ModelSelector models;
    private final StorybookProperties properties;

    @Override
    public JobStep step() {
        return JobStep.ILLUSTRATE_PAGE;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        int generation = job.getGeneration();
        PageContext ctx = persistence.pageContext(job.getStorybookId(), job.getPageIndex(), generation);
        if (ctx.status() == StorybookStatus.FAILED) {
            // Not "success": that would strand the page, because resume only revives dead jobs.
            return StepOutcome.fail("book is FAILED; this step reruns when it is resumed");
        }
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
        byte[] generated = null;
        if (!store.exists(key)) {
            boolean hasCompanion = ctx.companionSheetKey() != null;
            // A model takes only so many reference images (four for the cheaper one, five for Pro). Who must be recognisable on this
            // page comes first (the child, the companion and the supporting characters in the scene); the approved cover, which carries
            // the book's style, comes next; the generic style image only if there is still room.
            int max = models.maxReferencesFor(model);
            byte[] anchor = ctx.anchorKey() == null ? null : store.get(ctx.anchorKey());
            java.util.Set<String> inScene = new java.util.HashSet<>();
            ctx.cast().forEach(c -> inScene.add(c.ref()));
            java.util.List<com.doova.ktab.features.storybook.image.ReferenceImage> identity = new java.util.ArrayList<>();
            byte[] companionSheet = hasCompanion && inScene.contains("COMPANION") ? store.get(ctx.companionSheetKey()) : null;
            if (companionSheet != null) {
                identity.add(new com.doova.ktab.features.storybook.image.ReferenceImage(companionSheet, "image/png"));
            }
            java.util.List<CharacterPrompts.SupportingLook> looks = new java.util.ArrayList<>();
            for (PageContext.SupportingLook s : ctx.supporting()) {
                if (1 + identity.size() >= max) {
                    break; // the model cannot take another sheet
                }
                byte[] sheet = inScene.contains(s.ref()) && s.sheetKey() != null ? store.get(s.sheetKey()) : null;
                if (sheet != null) {
                    identity.add(new com.doova.ktab.features.storybook.image.ReferenceImage(sheet, "image/png"));
                    looks.add(new CharacterPrompts.SupportingLook(s.ref(), s.describeEn(), s.clothing()));
                }
            }
            int room = max - 1 - identity.size();
            boolean keepAnchor = anchor != null && room >= 1;
            boolean keepStyle = room - (keepAnchor ? 1 : 0) >= 1;
            var references = new java.util.ArrayList<com.doova.ktab.features.storybook.image.ReferenceImage>();
            references.add(new com.doova.ktab.features.storybook.image.ReferenceImage(store.get(ctx.childSheetKey()), "image/png"));
            if (keepStyle) {
                references.add(new com.doova.ktab.features.storybook.image.ReferenceImage(styles.get(ctx.style()), "image/png"));
            }
            references.addAll(identity);
            if (keepAnchor) {
                references.add(new com.doova.ktab.features.storybook.image.ReferenceImage(anchor, "image/png"));
            }
            CharacterPrompts.PageLock lock = new CharacterPrompts.PageLock(ctx.hijab(), ctx.glasses(), ctx.appearanceEn(),
                    ctx.childClothing(), ctx.companionEn(), ctx.styleNotes(), keepAnchor, looks, keepStyle);
            String prompt = ctx.kind() == PageKind.COVER
                    ? CharacterPrompts.cover(SceneText.withoutOutfit(ctx.sceneEn()), hasCompanion, lock,
                            ctx.setting(), ctx.timeOfDay(), ctx.place())
                    : CharacterPrompts.scene(SceneText.withoutOutfit(ctx.sceneEn()), ctx.textZone(), hasCompanion
                        && inScene.contains("COMPANION"), lock,
                            ctx.setting(), ctx.timeOfDay(), ctx.place());
            ImageResult result = images.generate(new ImageRequest(model, prompt, references));
            cost = ledger.recordImage(ctx.bookId(), job.getId(), "IMAGE_PAGE", result);
            store.put(key, result.bytes(), result.mimeType());
            generated = result.bytes();
        }
        String webKey = writeWebCopy(ctx.bookId(), ctx.pageIndex(), generation, key, generated);
        persistence.savePageImage(ctx.bookId(), ctx.pageId(), ctx.pageIndex(), generation, key, webKey, model, cost);
        return StepOutcome.success();
    }

    /**
     * Stores the JPEG copy that readers load (the PNG is several MB). Never fails the page: a copy that cannot be made
     * leaves the key null and readers fall back to the original. {@code generated} is null when the original was
     * already stored by an earlier attempt, so it is read back.
     */
    private String writeWebCopy(Long bookId, int pageIndex, int generation, String originalKey, byte[] generated) {
        String webKey = StorybookKeys.pageImageWeb(bookId, pageIndex, generation);
        try {
            if (!store.exists(webKey)) {
                byte[] original = generated != null ? generated : store.get(originalKey);
                byte[] jpeg = ImageDownscaler.toJpeg(original, properties.getImage().getWebMaxSidePx());
                store.put(webKey, jpeg, "image/jpeg");
            }
            return webKey;
        } catch (RuntimeException e) {
            log.warn("storybook {} page {} g{}: no web copy made, readers will get the original: {}",
                    bookId, pageIndex, generation, e.toString());
            return null;
        }
    }
}
