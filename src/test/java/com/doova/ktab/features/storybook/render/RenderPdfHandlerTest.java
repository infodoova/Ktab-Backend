package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RenderPdfHandlerTest {

    private final StorybookRepository books = mock(StorybookRepository.class);
    private final StorybookPageRepository pages = mock(StorybookPageRepository.class);
    private final StorybookAssetStore store = mock(StorybookAssetStore.class);
    private final StorybookHtmlComposer composer = mock(StorybookHtmlComposer.class);
    private final StorybookPdfRenderer pdfRenderer = mock(StorybookPdfRenderer.class);
    private final StorybookStateMachine stateMachine = mock(StorybookStateMachine.class);
    private RenderPdfHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RenderPdfHandler(books, pages, store, composer, pdfRenderer, stateMachine);
    }

    private static StorybookJob job(Long bookId) {
        StorybookJob j = new StorybookJob();
        j.setId(1L);
        j.setStorybookId(bookId);
        j.setStep(JobStep.RENDER_PDF);
        return j;
    }

    @Test
    void rendersPdfAndAdvancesBookToReady() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.RENDERING);
        book.setPageRegenerations(0);
        when(books.findById(10L)).thenReturn(Optional.of(book));

        StorybookPage page0 = new StorybookPage();
        page0.setPageIndex(0);
        StorybookPageImage img0 = new StorybookPageImage();
        img0.setImageKey("storybook/10/pages/0/g1.png");
        page0.setCurrentImage(img0);

        String expectedKey = StorybookKeys.pdf(10L, 0);
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(page0));
        when(store.exists(expectedKey)).thenReturn(false);
        when(store.get("storybook/10/pages/0/g1.png")).thenReturn(new byte[]{1, 2});
        when(composer.composeHtml(eq(book), anyList(), anyMap())).thenReturn("<html>PDF</html>");
        when(pdfRenderer.renderHtml("<html>PDF</html>")).thenReturn(new byte[]{9, 9, 9});

        StepOutcome outcome = handler.handle(job(10L));

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(store).put(eq(expectedKey), eq(new byte[]{9, 9, 9}), eq("application/pdf"));
        assertThat(book.getPdfKey()).isEqualTo(expectedKey);
        verify(stateMachine).transition(book, StorybookStatus.READY);
    }

    @Test
    void skipsRenderingIfAlreadyInStore() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.RENDERING);
        book.setPageRegenerations(1);
        String expectedKey = StorybookKeys.pdf(10L, 1);
        when(books.findById(10L)).thenReturn(Optional.of(book));
        when(store.exists(expectedKey)).thenReturn(true);

        StepOutcome outcome = handler.handle(job(10L));

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verifyNoInteractions(composer, pdfRenderer);
        assertThat(book.getPdfKey()).isEqualTo(expectedKey);
        verify(stateMachine).transition(book, StorybookStatus.READY);
    }

    @Test
    void skipsIfBookNotInRenderingState() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.READY);
        when(books.findById(10L)).thenReturn(Optional.of(book));

        StepOutcome outcome = handler.handle(job(10L));

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verifyNoInteractions(store, composer, pdfRenderer, stateMachine);
    }
}
