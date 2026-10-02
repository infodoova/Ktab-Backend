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
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png", null, false, java.util.Map.of());
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
        verify(persistence).saveSheets(eq(9L), eq(1), anyString(), isNull(), eq(true), anyMap());
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
                "storybook/9/characters/companion/v1.png", false, java.util.Map.of());
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
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png", null, false, java.util.Map.of());
    }

    private static SheetContext ctxWithClothing(String clothing, boolean companion) {
        SheetContext base = ctx(0, null, false, companion);
        return new SheetContext(base.bookId(), base.status(), base.storyApproved(), base.gender(), base.ageBand(),
                base.appearance(), base.companion(), base.style(), base.childSheetVersion(), base.childSheetKey(),
                base.photoKey(), base.photoBased(), base.companionSheetExists(), clothing);
    }

    @Test
    void theLockedOutfitIsSentWithTheSheetRequest() {
        when(persistence.sheetContext(9L)).thenReturn(ctxWithClothing("a soft turquoise hijab and a coral tunic", false));
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().prompt()).contains("a soft turquoise hijab and a coral tunic");
    }

    @Test
    void aSheetOnATintedBackdropIsDrawnAgain() throws Exception {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, false));
        byte[] yellow = SheetBackgroundCheckTest.png(new java.awt.Color(250, 235, 90), new java.awt.Color(40, 160, 90));
        byte[] white = SheetBackgroundCheckTest.png(java.awt.Color.WHITE, new java.awt.Color(40, 160, 90));
        when(images.generate(any()))
                .thenReturn(new ImageResult(yellow, "image/png", "m", 5))
                .thenReturn(new ImageResult(white, "image/png", "m", 5));

        handler.handle(job(1));

        verify(images, times(2)).generate(any());
        verify(store).put(eq("storybook/9/characters/child/v1.png"), eq(white), eq("image/png"));
        verify(ledger, times(2)).recordImage(eq(9L), eq(3L), eq("IMAGE_CHARACTER_SHEET"), any());
    }

    @Test
    void aSheetThatNeverGetsAWhiteBackdropIsKeptAfterThreeTriesNotFailed() throws Exception {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, false));
        byte[] yellow = SheetBackgroundCheckTest.png(new java.awt.Color(250, 235, 90), new java.awt.Color(40, 160, 90));
        when(images.generate(any())).thenReturn(new ImageResult(yellow, "image/png", "m", 5));

        assertThat(handler.handle(job(1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        verify(images, times(3)).generate(any());
        verify(store).put(eq("storybook/9/characters/child/v1.png"), eq(yellow), eq("image/png"));
    }

    @Test
    void theCompanionIsDrawnFromTheChildSheetAsAStyleGuide() {
        when(persistence.sheetContext(9L)).thenReturn(ctx(0, null, false, true));
        byte[] childSheet = new byte[]{1, 2, 3};
        when(images.generate(any()))
                .thenReturn(new ImageResult(childSheet, "image/png", "m", 5))
                .thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images, times(2)).generate(req.capture());
        ImageRequest companion = req.getAllValues().get(1);
        assertThat(companion.references()).hasSize(2);
        assertThat(companion.references().get(1).bytes()).isEqualTo(childSheet);
        assertThat(companion.prompt()).contains("second reference image is the child's character sheet");
    }

    private static SheetContext ctxWithSupporting(java.util.List<SheetContext.SupportingSheet> supporting) {
        SheetContext base = ctx(0, null, false, true);
        return new SheetContext(base.bookId(), base.status(), base.storyApproved(), base.gender(), base.ageBand(),
                base.appearance(), base.companion(), base.style(), base.childSheetVersion(), base.childSheetKey(),
                base.photoKey(), base.photoBased(), base.companionSheetExists(), null, supporting);
    }

    @Test
    void everySupportingCharacterGetsItsOwnSheetDrawnAfterTheChildAsAStyleGuide() {
        when(persistence.sheetContext(9L)).thenReturn(ctxWithSupporting(java.util.List.of(
                new SheetContext.SupportingSheet("grandpa", "SUPPORT_1", "the grandfather, a man, about 68 years old", "a brown jalabiya", null))));
        byte[] childSheet = new byte[]{1, 2, 3};
        when(images.generate(any()))
                .thenReturn(new ImageResult(childSheet, "image/png", "m", 5))
                .thenReturn(new ImageResult(new byte[]{4}, "image/png", "m", 5))
                .thenReturn(new ImageResult(new byte[]{5}, "image/png", "m", 5));

        handler.handle(job(1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images, times(3)).generate(req.capture());
        ImageRequest supporting = req.getAllValues().get(2);
        assertThat(supporting.prompt()).contains("the grandfather, a man, about 68 years old").contains("a brown jalabiya");
        assertThat(supporting.references()).hasSize(2);
        assertThat(supporting.references().get(1).bytes()).isEqualTo(childSheet);
        verify(store).put(eq("storybook/9/characters/supporting/grandpa/v1.png"), eq(new byte[]{5}), eq("image/png"));
        verify(ledger).recordImage(eq(9L), eq(3L), eq("IMAGE_SUPPORTING_SHEET"), any());
        verify(persistence).saveSheets(9L, 1, "storybook/9/characters/child/v1.png", "storybook/9/characters/companion/v1.png", false,
                java.util.Map.of("grandpa", "storybook/9/characters/supporting/grandpa/v1.png"));
    }

    @Test
    void aSupportingSheetThatAlreadyExistsIsNotDrawnAgain() {
        when(persistence.sheetContext(9L)).thenReturn(ctxWithSupporting(java.util.List.of(
                new SheetContext.SupportingSheet("grandpa", "SUPPORT_1", "the grandfather", null, "storybook/9/characters/supporting/grandpa/v1.png"))));
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));

        handler.handle(job(1));

        verify(images, times(2)).generate(any()); // child and companion only
    }
}
