package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RenderPdfHandlerTest {

    private final PlaywrightPdfRenderer renderer = mock(PlaywrightPdfRenderer.class);
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final RenderPersistence persistence = mock(RenderPersistence.class);
    private final RenderPdfHandler handler = new RenderPdfHandler(renderer, store, persistence);

    private static StorybookJob job(int round) {
        StorybookJob j = new StorybookJob();
        j.setStorybookId(9L);
        j.setStep(JobStep.RENDER_PDF);
        j.setGeneration(round);
        return j;
    }

    private static RenderContext ctx(StorybookStatus status) {
        return new RenderContext(9L, status, "يومي", "سامي", null, TashkeelLevel.FULL,
                List.of(new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                        new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ.", TextZone.TOP)),
                Map.of(0, "k0", 1, "k1"));
    }

    @Test
    void rendersStoresAndFinishes() {
        when(persistence.context(9L)).thenReturn(ctx(StorybookStatus.RENDERING));
        when(store.get(anyString())).thenReturn(new byte[]{1});
        when(renderer.render(any(), anyMap())).thenReturn(new byte[]{'%', 'P'});

        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);

        verify(renderer).render(any(), argThat(m -> m.keySet().equals(java.util.Set.of(0, 1))));
        verify(store).put(eq("storybook/9/book-r0.pdf"), any(), eq("application/pdf"));
        verify(persistence).finish(9L, "storybook/9/book-r0.pdf");
    }

    @Test
    void aStoredPdfIsNotRenderedAgain() {
        when(persistence.context(9L)).thenReturn(ctx(StorybookStatus.RENDERING));
        when(store.exists("storybook/9/book-r1.pdf")).thenReturn(true);

        handler.handle(job(1));

        verifyNoInteractions(renderer);
        verify(persistence).finish(9L, "storybook/9/book-r1.pdf");
    }

    @Test
    void staleJobDoesNothing() {
        when(persistence.context(9L)).thenReturn(ctx(StorybookStatus.READY));
        handler.handle(job(0));
        verifyNoInteractions(renderer, store);
    }

    @Test
    void refusesToRenderWhenAStoryPageHasNoImage() {
        // IllustrationPersistence#advance is supposed to guarantee every cover/story page has a QA-passed
        // image before the book reaches RENDERING; this pins the fallback if that invariant ever breaks,
        // so a book is never silently shipped with a blank page instead of failing loudly.
        RenderContext broken = new RenderContext(9L, StorybookStatus.RENDERING, "يومي", "سامي", null, TashkeelLevel.FULL,
                List.of(new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                        new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ.", TextZone.TOP)),
                Map.of(0, "k0")); // page 1's image key is missing
        when(persistence.context(9L)).thenReturn(broken);

        StepOutcome outcome = handler.handle(job(0));

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.FAIL);
        assertThat(outcome.reason()).contains("page 1");
        verifyNoInteractions(renderer);
        verify(store, never()).put(anyString(), any(), anyString());
        verify(persistence, never()).finish(anyLong(), anyString());
    }
}
