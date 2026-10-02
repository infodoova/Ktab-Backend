package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.image.ImageProvider;
import com.doova.ktab.features.storybook.image.ImageRequest;
import com.doova.ktab.features.storybook.image.ImageResult;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.story.CharacterInScene;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PageIllustrationHandlerTest {

    private final ImageProvider images = mock(ImageProvider.class);
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final IllustrationPersistence persistence = mock(IllustrationPersistence.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final PageIllustrationHandler handler = new PageIllustrationHandler(images, store, persistence, ledger,
            new StyleReferences(), new ModelSelector(new StorybookProperties()));

    @BeforeEach
    void setUp() {
        when(store.get("child-sheet")).thenReturn(new byte[]{1});
        when(store.get("companion-sheet")).thenReturn(new byte[]{3});
        when(images.generate(any())).thenReturn(new ImageResult(new byte[]{9}, "image/png", "m", 5));
        when(ledger.recordImage(any(), any(), any(), any())).thenReturn(new BigDecimal("0.101"));
    }

    private static StorybookJob job(int pageIndex, int generation) {
        StorybookJob j = new StorybookJob();
        j.setId(11L);
        j.setStorybookId(9L);
        j.setStep(JobStep.ILLUSTRATE_PAGE);
        j.setPageIndex(pageIndex);
        j.setGeneration(generation);
        return j;
    }

    private static PageContext ctx(int pageIndex, int pageGeneration, int roundStart, boolean rowExists,
                                   StorybookStatus status, List<CharacterInScene> cast) {
        return new PageContext(9L, status, 100L, pageIndex, pageIndex == 0 ? PageKind.COVER : PageKind.STORY,
                "The CHILD waves.", TextZone.TOP, cast, pageGeneration, roundStart, rowExists,
                "child-sheet", "companion-sheet", ArtStyle.SOFT_WATERCOLOR, false);
    }

    @Test
    void illustratesAPageWithChildStyleAndCompanionReferences() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(ctx(3, 1, 1, false, StorybookStatus.ILLUSTRATING,
                List.of(new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "happy"))));

        assertThat(handler.handle(job(3, 1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().model()).isEqualTo("gemini-3.1-flash-image");
        assertThat(req.getValue().references()).hasSize(3);
        assertThat(req.getValue().prompt()).contains("The CHILD waves.").contains("top third");
        verify(store).put(eq("storybook/9/pages/3/g1.png"), any(), eq("image/png"));
        verify(persistence).savePageImage(9L, 100L, 3, 1, "storybook/9/pages/3/g1.png",
                "gemini-3.1-flash-image", new BigDecimal("0.101"));
    }

    @Test
    void thirdAttemptInARoundUsesTheFallbackModel() {
        when(persistence.pageContext(9L, 3, 3)).thenReturn(ctx(3, 3, 1, false, StorybookStatus.ILLUSTRATING, List.of()));
        handler.handle(job(3, 3));
        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().model()).isEqualTo("gemini-3-pro-image");
    }

    @Test
    void aParentRegenerationRoundStartsAgainOnThePrimaryModel() {
        when(persistence.pageContext(9L, 3, 5)).thenReturn(ctx(3, 5, 5, false, StorybookStatus.ILLUSTRATING, List.of()));
        handler.handle(job(3, 5));
        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().model()).isEqualTo("gemini-3.1-flash-image");
    }

    @Test
    void doesNotRegenerateWhenVersionAlreadyStored() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(ctx(3, 1, 1, true, StorybookStatus.ILLUSTRATING, List.of()));

        assertThat(handler.handle(job(3, 1)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        verifyNoInteractions(images, ledger);
        verify(persistence).ensureQaEnqueued(9L, 3, 1);
    }

    @Test
    void reusesAnUploadedObjectWithoutARow() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(ctx(3, 1, 1, false, StorybookStatus.ILLUSTRATING, List.of()));
        when(store.exists("storybook/9/pages/3/g1.png")).thenReturn(true);

        handler.handle(job(3, 1));

        verifyNoInteractions(images, ledger);
        verify(persistence).savePageImage(9L, 100L, 3, 1, "storybook/9/pages/3/g1.png",
                "gemini-3.1-flash-image", BigDecimal.ZERO);
    }

    @Test
    void supersededGenerationsAndStaleBooksDoNothing() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(ctx(3, 2, 1, false, StorybookStatus.ILLUSTRATING, List.of()));
        handler.handle(job(3, 1));
        when(persistence.pageContext(9L, 4, 1)).thenReturn(ctx(4, 1, 1, false, StorybookStatus.FAILED, List.of()));
        handler.handle(job(4, 1));
        verifyNoInteractions(images);
    }

    @Test
    void theCoverUsesTheCoverPrompt() {
        when(persistence.pageContext(9L, 0, 1)).thenReturn(ctx(0, 1, 1, false, StorybookStatus.ILLUSTRATING,
                List.of(new CharacterInScene("CHILD", "happy"))));
        handler.handle(job(0, 1));
        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().prompt()).contains("book cover");
    }

    private static PageContext lockedCtx(int pageIndex, String anchorKey) {
        PageContext base = ctx(pageIndex, 1, 1, false, StorybookStatus.ILLUSTRATING,
                List.of(new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "happy")));
        return new PageContext(base.bookId(), base.status(), base.pageId(), base.pageIndex(), base.kind(), base.sceneEn(),
                base.textZone(), base.cast(), base.pageGeneration(), base.roundStartGeneration(), base.imageRowExists(),
                base.childSheetKey(), base.companionSheetKey(), base.style(), true, anchorKey, false,
                "light olive skin, hazel eyes", "a turquoise hijab, a coral tunic and navy trousers",
                "a small green pet parrot", "soft watercolor, warm light, thin brown outlines");
    }

    @Test
    void thePagePromptCarriesTheIdentityLockAndTheBooksStyleNotes() {
        when(store.get("anchor-key")).thenReturn(new byte[]{8});
        when(persistence.pageContext(9L, 3, 1)).thenReturn(lockedCtx(3, "anchor-key"));

        handler.handle(job(3, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().prompt())
                .contains("wears NO glasses")
                .contains("a turquoise hijab, a coral tunic and navy trousers")
                .contains("light olive skin, hazel eyes")
                .contains("a small green pet parrot")
                .contains("soft watercolor, warm light, thin brown outlines");
    }

    @Test
    void theApprovedCoverIsTheLastReferenceSoTheOtherReferencesKeepTheirPlaces() {
        when(store.get("anchor-key")).thenReturn(new byte[]{8});
        when(persistence.pageContext(9L, 3, 1)).thenReturn(lockedCtx(3, "anchor-key"));

        handler.handle(job(3, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references()).hasSize(4);
        assertThat(req.getValue().references().get(3).bytes()).containsExactly(8);
        assertThat(req.getValue().prompt()).contains("last reference image is the approved book cover");
    }

    @Test
    void theCoverItselfHasNoAnchor() {
        when(persistence.pageContext(9L, 0, 1)).thenReturn(lockedCtx(0, null));

        handler.handle(job(0, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().prompt()).doesNotContain("approved book cover");
        assertThat(req.getValue().prompt()).contains("wears NO glasses");
    }

    @Test
    void anOutfitInAStoredSceneNeverReachesThePicturePrompt() {
        PageContext base = ctx(3, 1, 1, false, StorybookStatus.ILLUSTRATING, List.of(new CharacterInScene("CHILD", "happy")));
        PageContext dirty = new PageContext(base.bookId(), base.status(), base.pageId(), base.pageIndex(), base.kind(),
                "CHILD waves. CHILD wears her fixed outfit and fully covering lavender hijab; the calm lower third is a wall.",
                base.textZone(), base.cast(), base.pageGeneration(), base.roundStartGeneration(), base.imageRowExists(),
                base.childSheetKey(), base.companionSheetKey(), base.style(), true);
        when(persistence.pageContext(9L, 3, 1)).thenReturn(dirty);

        handler.handle(job(3, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().prompt()).doesNotContain("lavender").contains("CHILD waves");
    }

    private static PageContext supportingCtx(java.util.List<CharacterInScene> cast, String anchorKey, String sheetKey) {
        PageContext base = lockedCtx(3, anchorKey);
        return new PageContext(base.bookId(), base.status(), base.pageId(), base.pageIndex(), base.kind(), "CHILD shows SUPPORT_1 the map.",
                base.textZone(), cast, base.pageGeneration(), base.roundStartGeneration(), base.imageRowExists(),
                base.childSheetKey(), base.companionSheetKey(), base.style(), true, anchorKey, false,
                base.appearanceEn(), base.childClothing(), base.companionEn(), base.styleNotes(),
                java.util.List.of(new PageContext.SupportingLook("SUPPORT_1", "the grandfather, a man, about 68 years old",
                        "a brown jalabiya", sheetKey)));
    }

    @Test
    void aSupportingCharacterInTheSceneBringsItsSheetAfterTheCompanionAndBeforeTheCover() {
        when(store.get("anchor-key")).thenReturn(new byte[]{8});
        when(store.get("grandpa-sheet")).thenReturn(new byte[]{7});
        when(persistence.pageContext(9L, 3, 1)).thenReturn(supportingCtx(java.util.List.of(
                new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "happy"),
                new CharacterInScene("SUPPORT_1", "calm")), "anchor-key", "grandpa-sheet"));

        handler.handle(job(3, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        // the cheaper model takes four references: the style image gives way, the cover anchor stays
        assertThat(req.getValue().references()).hasSize(4); // child, companion, grandpa, cover
        assertThat(req.getValue().references().get(2).bytes()).containsExactly(7);
        assertThat(req.getValue().references().get(3).bytes()).containsExactly(8);
        assertThat(req.getValue().prompt()).contains("Identity lock for SUPPORT_1").contains("a brown jalabiya")
                .contains("Reference images, in order: CHILD sheet, COMPANION sheet, SUPPORT_1 sheet, approved book cover");
    }

    @Test
    void aSupportingCharacterWhoIsNotInThisSceneAddsNothing() {
        when(store.get("anchor-key")).thenReturn(new byte[]{8});
        when(persistence.pageContext(9L, 3, 1)).thenReturn(supportingCtx(java.util.List.of(
                new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "happy")), "anchor-key", "grandpa-sheet"));

        handler.handle(job(3, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references()).hasSize(4);
        assertThat(req.getValue().prompt()).doesNotContain("Identity lock for SUPPORT_1");
    }

    @Test
    void aSupportingCharacterWithoutASheetYetIsLeftOut() {
        when(persistence.pageContext(9L, 3, 1)).thenReturn(supportingCtx(java.util.List.of(
                new CharacterInScene("CHILD", "happy"), new CharacterInScene("SUPPORT_1", "calm")), null, null));

        handler.handle(job(3, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().prompt()).doesNotContain("Identity lock for SUPPORT_1");
    }

    private static PageContext everyoneCtx(int pageGeneration) {
        PageContext base = lockedCtx(3, "anchor-key");
        return new PageContext(base.bookId(), base.status(), base.pageId(), base.pageIndex(), base.kind(),
                "CHILD, SUPPORT_1 and SUPPORT_2 look at the map.", base.textZone(),
                java.util.List.of(new CharacterInScene("CHILD", "happy"), new CharacterInScene("COMPANION", "happy"),
                        new CharacterInScene("SUPPORT_1", "calm"), new CharacterInScene("SUPPORT_2", "happy")),
                pageGeneration, 1, false, base.childSheetKey(), base.companionSheetKey(), base.style(), true, "anchor-key", false,
                base.appearanceEn(), base.childClothing(), base.companionEn(), base.styleNotes(),
                java.util.List.of(
                        new PageContext.SupportingLook("SUPPORT_1", "the grandfather", "a brown jalabiya", "grandpa-sheet"),
                        new PageContext.SupportingLook("SUPPORT_2", "the friend", "a green dress", "friend-sheet")));
    }

    @Test
    void allFourCharactersOnTheCheaperModelFitItsFourReferencesWithIdentityFirst() {
        when(store.get("grandpa-sheet")).thenReturn(new byte[]{7});
        when(store.get("friend-sheet")).thenReturn(new byte[]{6});
        when(store.get("anchor-key")).thenReturn(new byte[]{8});
        when(persistence.pageContext(9L, 3, 1)).thenReturn(everyoneCtx(1));

        handler.handle(job(3, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().model()).isEqualTo("gemini-3.1-flash-image");
        assertThat(req.getValue().references()).hasSize(4); // child, companion, grandpa, friend: no style image, no cover
        assertThat(req.getValue().references().get(2).bytes()).containsExactly(7);
        assertThat(req.getValue().references().get(3).bytes()).containsExactly(6);
        assertThat(req.getValue().prompt()).contains("Reference images, in order: CHILD sheet, COMPANION sheet, SUPPORT_1 sheet, SUPPORT_2 sheet")
                .doesNotContain("approved book cover:").contains("Identity lock for SUPPORT_2")
                .contains("Art direction for the whole book");
    }

    @Test
    void allFourCharactersOnTheProModelAlsoKeepTheCoverAnchor() {
        when(store.get("grandpa-sheet")).thenReturn(new byte[]{7});
        when(store.get("friend-sheet")).thenReturn(new byte[]{6});
        when(store.get("anchor-key")).thenReturn(new byte[]{8});
        when(persistence.pageContext(9L, 3, 3)).thenReturn(everyoneCtx(3));

        handler.handle(job(3, 3)); // the third attempt in a round escalates to the Pro model

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().model()).isEqualTo("gemini-3-pro-image");
        assertThat(req.getValue().references()).hasSize(5); // child, companion, grandpa, friend, cover
        assertThat(req.getValue().references().get(4).bytes()).containsExactly(8);
        assertThat(req.getValue().prompt()).contains("Reference images, in order: CHILD sheet, COMPANION sheet, SUPPORT_1 sheet, SUPPORT_2 sheet, approved book cover");
    }

    @Test
    void aTwoCharacterPageKeepsEverythingItAlwaysHad() {
        when(store.get("anchor-key")).thenReturn(new byte[]{8});
        when(persistence.pageContext(9L, 3, 1)).thenReturn(lockedCtx(3, "anchor-key"));

        handler.handle(job(3, 1));

        ArgumentCaptor<ImageRequest> req = ArgumentCaptor.forClass(ImageRequest.class);
        verify(images).generate(req.capture());
        assertThat(req.getValue().references()).hasSize(4); // child, style, companion, cover
    }
}
