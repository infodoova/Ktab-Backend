package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.PhotoVault;
import com.doova.ktab.features.storybook.character.PhotoVaultTest;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.image.ImageProvider;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CharacterSheetHandlerTest {

    private final ImageProvider images = mock(ImageProvider.class);
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final IllustrationPersistence persistence = mock(IllustrationPersistence.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final PhotoVault vault = new PhotoVault(PhotoVaultTest.withKey());
    private final CharacterSheetHandler handler = new CharacterSheetHandler(images, store, persistence, ledger, vault,
            new StyleReferences(), new ModelSelector(new StorybookProperties()));

    private static StorybookJob job(int version) {
        StorybookJob j = new StorybookJob();
        j.setId(3L);
        j.setStorybookId(9L);
        j.setStep(JobStep.CHARACTER_SHEET);
        j.setGeneration(version);
        return j;
    }

    private static SheetContext ctx(int childVersion, String photoKey, boolean photoBased, boolean companion) {
        return new SheetContext(9L, StorybookStatus.STORY_READY, true, ChildGender.GIRL, AgeBand.AGE_6_8,
                StoryFixtures.APPEARANCE,
                companion ? new com.doova.ktab.features.storybook.character.CompanionSpec(
                        com.doova.ktab.features.storybook.character.CompanionSpec.CompanionType.CAT, "بسبوسة", null,
                        com.doova.ktab.features.storybook.character.CompanionSpec.PetColor.ORANGE) : null,
                ArtStyle.SOFT_WATERCOLOR, childVersion, childVersion == 0 ? null : "old-sheet", photoKey, photoBased, false);
    }

    @Test
    void generatesFromAttributesWhenThereIsNoPhoto() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, false));
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "gemini-3.1-flash-image", 5));

        assertThat(handler.handle(job(1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references()).hasSize(1); // style only
        assertThat(req.getValue().prompt()).contains("girl").contains("front view");
        verify(store).put(eq("storybook/9/characters/child/v1.png"), any(), eq("image/png"));
        verify(ledger).recordImage(eq(9L), eq(3L), eq("IMAGE_CHARACTER_SHEET"), any());
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png", null, false);
    }

    @Test
    void usesThePhotoAndAsksForItsPurge() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, "storybook/9/photo/source.enc", true, false));
        when(store.get("storybook/9/photo/source.enc")).thenReturn(vault.encrypt(new byte[]{7}));
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references().get(0).bytes()).containsExactly(7);
        assertThat(req.getValue().prompt()).contains("photo reference");
        verify(persistence).saveSheets(eq(9L), eq(1), anyString(), isNull(), eq(true));
    }

    @Test
    void afterThePhotoIsGoneANewLookStartsFromThePreviousSheet() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(1, null, true, false));
        when(store.get("old-sheet")).thenReturn(new byte[]{5});
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(2));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references().get(0).bytes()).containsExactly(5);
        assertThat(req.getValue().prompt()).contains("new version of the character");
    }

    @Test
    void alsoDrawsTheCompanionOnce() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, true));
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(1));

        verify(images, times(2)).generate(any());
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png",
                "storybook/9/characters/companion/v1.png", false);
    }

    @Test
    void doneVersionsAreSkipped() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(1, null, false, false));
        assertThat(handler.handle(job(1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verifyNoInteractions(images, ledger);
    }

    @Test
    void anUploadedSheetIsReusedWithoutPayingAgain() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, false));
        when(store.exists("storybook/9/characters/child/v1.png")).thenReturn(true);

        handler.handle(job(1));

        verifyNoInteractions(images);
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png", null, false);
    }
}
