package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.StepHandler;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.storage.StorybookAssetStore;
import com.doova.ktab.features.storybook.storage.StorybookKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RenderPdfHandler implements StepHandler {

    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final StorybookAssetStore store;
    private final StorybookHtmlComposer composer;
    private final StorybookPdfRenderer pdfRenderer;
    private final StorybookStateMachine stateMachine;

    @Override
    public JobStep step() {
        return JobStep.RENDER_PDF;
    }

    @Override
    @Transactional
    public StepOutcome handle(StorybookJob job) {
        Long bookId = job.getStorybookId();
        Storybook book = books.findById(bookId).orElseThrow();
        if (book.getStatus() != StorybookStatus.RENDERING) {
            return StepOutcome.success();
        }

        String pdfKey = StorybookKeys.pdf(bookId, book.getPageRegenerations());
        if (!store.exists(pdfKey)) {
            List<StorybookPage> pageList = pages.findByStorybook_IdOrderByPageIndexAsc(bookId);
            Map<Integer, byte[]> images = new HashMap<>();
            for (StorybookPage page : pageList) {
                if (page.getCurrentImage() != null && page.getCurrentImage().getImageKey() != null) {
                    byte[] bytes = store.get(page.getCurrentImage().getImageKey());
                    images.put((int) page.getPageIndex(), bytes);
                }
            }
            String html = composer.composeHtml(book, pageList, images);
            byte[] pdfBytes = pdfRenderer.renderHtml(html);
            store.put(pdfKey, pdfBytes, "application/pdf");
            log.info("Rendered and stored PDF for bookId={} at key={}", bookId, pdfKey);
        }

        book.setPdfKey(pdfKey);
        stateMachine.transition(book, StorybookStatus.READY);
        return StepOutcome.success();
    }
}
