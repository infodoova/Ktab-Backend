package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.CharacterPrompts;
import com.doova.ktab.features.storybook.character.PhotoVault;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.image.ImageProvider;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.image.ReferenceImage;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CharacterSheetHandler implements StepHandler {

    private final ImageProvider images;
    private final StorybookAssetStore store;
    private final IllustrationPersistence persistence;
    private final AiCallLedger ledger;
    private final PhotoVault vault;
    private final StyleReferences styles;
    private final ModelSelector models;

    @Override
    public JobStep step() {
        return JobStep.CHARACTER_SHEET;
    }

    @Override
    public StepOutcome handle(StorybookJob job) {
        SheetContext ctx = persistence.sheetContext(job.getStorybookId());
        boolean ready = (ctx.status() == StorybookStatus.STORY_READY && ctx.storyApproved())
                || ctx.status() == StorybookStatus.CHARACTER_READY;
        int version = job.getGeneration();
        if (!ready || ctx.childSheetVersion() >= version) {
            return StepOutcome.success();
        }
        byte[] style = styles.get(ctx.style());
        String model = models.modelFor(1);

        String childKey = StorybookKeys.characterSheet(ctx.bookId(), CharacterKind.CHILD, version);
        boolean photoUsed = false;
        if (!store.exists(childKey)) {
            ImageRequest request;
            if (ctx.photoKey() != null) {
                byte[] photo = vault.decrypt(store.get(ctx.photoKey()));
                request = new ImageRequest(model, CharacterPrompts.sheetFromPhoto(ctx.gender(), ctx.ageBand()),
                        List.of(new ReferenceImage(photo, "image/jpeg"), new ReferenceImage(style, "image/png")));
                photoUsed = true;
            } else if (ctx.photoBased() && ctx.childSheetKey() != null) {
                request = new ImageRequest(model, CharacterPrompts.sheetFromPreviousSheet(ctx.gender(), ctx.ageBand()),
                        List.of(new ReferenceImage(store.get(ctx.childSheetKey()), "image/png"), new ReferenceImage(style, "image/png")));
            } else {
                request = new ImageRequest(model, CharacterPrompts.sheet(ctx.gender(), ctx.ageBand(), ctx.appearance()),
                        List.of(new ReferenceImage(style, "image/png")));
            }
            ImageResult result = images.generate(request);
            ledger.recordImage(ctx.bookId(), job.getId(), "IMAGE_CHARACTER_SHEET", result);
            store.put(childKey, result.bytes(), result.mimeType());
        } else if (ctx.photoKey() != null) {
            photoUsed = true; // sheet was made from the photo before a crash; still purge it
        }

        String companionKey = null;
        if (ctx.companion() != null && !ctx.companionSheetExists()) {
            companionKey = StorybookKeys.characterSheet(ctx.bookId(), CharacterKind.COMPANION, 1);
            if (!store.exists(companionKey)) {
                ImageResult result = images.generate(new ImageRequest(model,
                        CharacterPrompts.companionSheet(ctx.companion()), List.of(new ReferenceImage(style, "image/png"))));
                ledger.recordImage(ctx.bookId(), job.getId(), "IMAGE_COMPANION_SHEET", result);
                store.put(companionKey, result.bytes(), result.mimeType());
            }
        }

        persistence.saveSheets(ctx.bookId(), version, childKey, companionKey, photoUsed);
        return StepOutcome.success();
    }
}
