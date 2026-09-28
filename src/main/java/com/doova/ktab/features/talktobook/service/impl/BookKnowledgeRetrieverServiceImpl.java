package com.doova.ktab.features.talktobook.service.impl;

import com.doova.ktab.features.talktobook.dto.RetrievedContext;
import com.doova.ktab.features.talktobook.dto.response.TalkToBookResponse.PageExcerpt;
import com.doova.ktab.features.talktobook.service.BookKnowledgeRetrieverService;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookKnowledgeRetrieverServiceImpl implements BookKnowledgeRetrieverService {

    private static final int EXCERPT_MAX_CHARS = 260;

    private final BookPageRepository bookPageRepository;
    private final BookSectionRepository bookSectionRepository;

    @Override
    @Transactional(readOnly = true)
    public RetrievedContext retrievePinpointContext(Long bookId, String question) {
        log.debug("Retrieving pinpoint context for book {} on question: '{}'", bookId, question);

        // Clean and prepare query terms for PostgreSQL full-text search
        String sanitizedQuery = question.replaceAll("[^\\p{L}\\p{Nd}\\s]", " ").trim();
        List<BookPage> matchingPages = List.of();

        if (!sanitizedQuery.isBlank()) {
            matchingPages = bookPageRepository.searchPagesFullText(bookId, sanitizedQuery, 4);
        }

        // Fallback: If no direct full-text hits, sample early pages that have actual content
        if (matchingPages.isEmpty()) {
            log.debug("No full-text matches found, fetching initial pages with content as fallback context");
            matchingPages = bookPageRepository.findFirstPagesWithContent(bookId, 4);
        }

        StringBuilder sb = new StringBuilder();
        List<Integer> citedPages = new ArrayList<>();
        Map<Integer, PageExcerpt> excerpts = new LinkedHashMap<>();

        for (BookPage page : matchingPages) {
            String text = page.getMarkdownClean() != null ? page.getMarkdownClean() : page.getMarkdownContent();
            if (text != null && !text.isBlank()) {
                int pageNum = page.getPageNumber();
                citedPages.add(pageNum);
                sb.append(String.format("--- [صفحة %d] ---\n%s\n\n", pageNum, text.trim()));
                excerpts.put(pageNum, buildExcerpt(page, text));
            }
        }

        return new RetrievedContext(sb.toString().trim(), citedPages, excerpts);
    }

    @Override
    @Transactional(readOnly = true)
    public RetrievedContext retrieveMacroContext(Long bookId) {
        log.debug("Retrieving macro structure context for book {}", bookId);

        List<BookSection> sections = bookSectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);
        StringBuilder sb = new StringBuilder();

        if (!sections.isEmpty()) {
            sb.append("=== فهرس وهيكل فصول الكتاب ===\n");
            for (BookSection section : sections) {
                sb.append(String.format("- %s (الصفحات %s إلى %s)\n",
                        section.getTitle(),
                        section.getStartPage() != null ? section.getStartPage() : "؟",
                        section.getEndPage() != null ? section.getEndPage() : "؟"));
            }
            sb.append("\n");
        }

        // Fetch representative early pages that have actual text content
        List<BookPage> earlyPages = bookPageRepository.findFirstPagesWithContent(bookId, 6);
        List<Integer> citedPages = new ArrayList<>();
        Map<Integer, PageExcerpt> excerpts = new LinkedHashMap<>();

        if (!earlyPages.isEmpty()) {
            sb.append("=== مقتطفات من صفحات الكتاب المتوفرة ===\n");
            for (BookPage page : earlyPages) {
                String text = page.getMarkdownClean() != null ? page.getMarkdownClean() : page.getMarkdownContent();
                if (text != null && !text.isBlank()) {
                    int pageNum = page.getPageNumber();
                    citedPages.add(pageNum);
                    sb.append(String.format("[صفحة %d]:\n%s\n\n", pageNum, text.trim()));
                    excerpts.put(pageNum, buildExcerpt(page, text));
                }
            }
        }

        return new RetrievedContext(sb.toString().trim(), citedPages, excerpts);
    }

    /**
     * Builds a short PageExcerpt from a BookPage.
     * Strips tashkeel diacritics and trims to EXCERPT_MAX_CHARS for compact tooltip display.
     */
    private PageExcerpt buildExcerpt(BookPage page, String fullText) {
        // Strip tashkeel (Arabic diacritics) for cleaner display
        String clean = fullText.trim().replaceAll("[\u064B-\u065F\u0670]", "");
        // Trim sentences cleanly at a word boundary
        String excerpt = clean.length() > EXCERPT_MAX_CHARS
                ? clean.substring(0, EXCERPT_MAX_CHARS).replaceAll("\\s+\\S*$", "…")
                : clean;

        String printedLabel = page.getPrintedPageLabel() != null && !page.getPrintedPageLabel().isBlank()
                ? page.getPrintedPageLabel()
                : String.valueOf(page.getPageNumber());

        return new PageExcerpt(page.getPageNumber(), printedLabel, excerpt);
    }
}
