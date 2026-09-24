package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RenderPersistence {

    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final StorybookStateMachine stateMachine;

    @Transactional(readOnly = true)
    public RenderContext context(Long bookId) {
        Storybook book = books.findById(bookId).orElseThrow();
        List<StorybookPage> stored = pages.findByStorybook_IdOrderByPageIndexAsc(bookId);
        Map<Integer, String> keys = new HashMap<>();
        for (StorybookPage p : stored) {
            if (p.getCurrentImage() != null) {
                keys.put((int) p.getPageIndex(), p.getCurrentImage().getImageKey());
            }
        }
        return new RenderContext(bookId, book.getStatus(), book.getTitleAr(), book.getInputs().childNameAr(),
                book.getDedication(), book.getTashkeelLevel(),
                stored.stream().map(p -> new RenderModelFactory.PageSource(p.getPageIndex(), p.getKind(), p.getTextAr(), p.getTextZone())).toList(),
                keys);
    }

    @Transactional
    public void finish(Long bookId, String pdfKey) {
        Storybook book = books.findById(bookId).orElseThrow();
        if (book.getStatus() != StorybookStatus.RENDERING) {
            return;
        }
        book.setPdfKey(pdfKey);
        stateMachine.transition(book, StorybookStatus.READY);
    }
}
