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
}
