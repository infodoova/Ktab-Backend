package com.doova.ktab.features.talktobook.service;

import com.doova.ktab.features.extraction.BookExtractionQueryService;
import com.doova.ktab.features.talktobook.dto.RetrievedContext;
import com.doova.ktab.features.talktobook.service.impl.BookKnowledgeRetrieverServiceImpl;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookKnowledgeRetrieverServiceTest {

    private final BookPageRepository pageRepository = mock(BookPageRepository.class);
    private final BookSectionRepository sectionRepository = mock(BookSectionRepository.class);
    private final BookExtractionQueryService extractionQueryService = mock(BookExtractionQueryService.class);
    private final BookKnowledgeRetrieverServiceImpl retriever =
            new BookKnowledgeRetrieverServiceImpl(pageRepository, sectionRepository, extractionQueryService);

    @Test
    void pinpointFallbackFindsRelevantLaterPage() {
        List<BookPage> pages = pages(20);
        pages.get(15).setMarkdownClean("تصف هذه الصفحة أثر الذاكرة العراقية في حياة الناس وتحولات المجتمع");
        when(extractionQueryService.getReaderSourcePages(114L)).thenReturn(pages);
        when(pageRepository.searchPagesFullText(114L, "أثر الذاكرة العراقية", 40)).thenReturn(List.of());

        RetrievedContext context = retriever.retrievePinpointContext(114L, "أثر الذاكرة العراقية");

        assertThat(context.citedPages()).contains(16);
        assertThat(context.contextText()).contains("الذاكرة العراقية");
    }

    @Test
    void macroContextSamplesBeginningMiddleAndEnd() {
        List<BookPage> pages = pages(18);
        when(extractionQueryService.getReaderSourcePages(114L)).thenReturn(pages);
        when(sectionRepository.findByBook_IdOrderBySortOrderAsc(114L)).thenReturn(List.of());

        RetrievedContext context = retriever.retrieveMacroContext(114L);

        assertThat(context.citedPages()).hasSize(6).contains(1, 18);
        assertThat(context.citedPages()).anyMatch(page -> page >= 8 && page <= 12);
    }

    private List<BookPage> pages(int count) {
        List<BookPage> pages = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            BookPage page = new BookPage();
            page.setPageNumber(index);
            page.setMarkdownClean("صفحة الكتاب تقدم سردا مفصلا عن التاريخ والأحداث والقرارات عبر سنوات طويلة");
            pages.add(page);
        }
        return pages;
    }
}
