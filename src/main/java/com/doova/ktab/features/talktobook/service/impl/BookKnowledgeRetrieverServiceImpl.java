package com.doova.ktab.features.talktobook.service.impl;

import com.doova.ktab.features.talktobook.dto.RetrievedContext;
import com.doova.ktab.features.extraction.BookExtractionQueryService;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookKnowledgeRetrieverServiceImpl implements BookKnowledgeRetrieverService {

    private static final int EXCERPT_MAX_CHARS = 260;
    private static final Set<String> SEARCH_STOPWORDS = Set.of(
            "the", "this", "that", "what", "which", "who", "when", "where", "why", "how",
            "is", "are", "was", "were", "of", "in", "on", "for", "and", "to", "a", "an",
            "book", "author", "about", "tell", "me", "please",
            "ما", "من", "في", "عن", "على", "الى", "هل", "كيف", "ماذا", "هذا", "هذه",
            "الكتاب", "كتاب", "للكتاب", "كاتب", "مؤلف", "مع", "التي", "الذي");

    private final BookPageRepository bookPageRepository;
    private final BookSectionRepository bookSectionRepository;
    private final BookExtractionQueryService extractionQueryService;

    @Override
    @Transactional(readOnly = true)
    public RetrievedContext retrievePinpointContext(Long bookId, String question) {
        log.debug("Retrieving pinpoint context for book {} on question: '{}'", bookId, question);

        // Clean and prepare query terms for PostgreSQL full-text search
        String sanitizedQuery = question.replaceAll("[^\\p{L}\\p{Nd}\\s]", " ").trim();
        List<BookPage> readerPages = extractionQueryService.getReaderSourcePages(bookId).stream()
                .filter(this::hasReaderText).toList();
        Set<Integer> readerPageNumbers = readerPages.stream()
                .map(BookPage::getPageNumber).collect(java.util.stream.Collectors.toSet());
        List<BookPage> matchingPages = List.of();

        if (!sanitizedQuery.isBlank()) {
            List<BookPage> fullTextHits = bookPageRepository.searchPagesFullText(bookId, sanitizedQuery, 40)
                    .stream().filter(page -> readerPageNumbers.contains(page.getPageNumber())).toList();
            matchingPages = selectDiversePages(fullTextHits, 4, readerPages.size());
        }

        if (matchingPages.isEmpty()) {
            matchingPages = rankPagesByQuestion(readerPages, question, 4);
            if (matchingPages.isEmpty()) {
                log.debug("No lexical matches for book {}; sampling body pages across the book", bookId);
                matchingPages = sampleAcrossBook(readerPages, 4);
            }
        }

        StringBuilder sb = new StringBuilder();
        List<Integer> citedPages = new ArrayList<>();
        Map<Integer, PageExcerpt> excerpts = new LinkedHashMap<>();

        for (BookPage page : matchingPages) {
            String text = page.getMarkdownClean() != null && !page.getMarkdownClean().isBlank()
                    ? page.getMarkdownClean() : page.getMarkdownContent();
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

        // Represent the full body rather than repeatedly citing its opening pages.
        List<BookPage> samplePages = sampleAcrossBook(
                extractionQueryService.getReaderSourcePages(bookId).stream()
                        .filter(this::hasReaderText).toList(), 6);
        List<Integer> citedPages = new ArrayList<>();
        Map<Integer, PageExcerpt> excerpts = new LinkedHashMap<>();

        if (!samplePages.isEmpty()) {
            sb.append("=== مقتطفات من صفحات الكتاب المتوفرة ===\n");
            for (BookPage page : samplePages) {
                String text = page.getMarkdownClean() != null && !page.getMarkdownClean().isBlank()
                        ? page.getMarkdownClean() : page.getMarkdownContent();
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

    private record ScoredPage(BookPage page, int score) {}

    private List<BookPage> rankPagesByQuestion(List<BookPage> pages, String question, int limit) {
        Set<String> terms = searchTerms(question);
        if (terms.isEmpty()) return List.of();
        List<ScoredPage> ranked = pages.stream()
                .map(page -> {
                    String text = page.getMarkdownClean() != null && !page.getMarkdownClean().isBlank()
                            ? page.getMarkdownClean() : page.getMarkdownContent();
                    Set<String> pageTerms = searchTerms(text);
                    int score = (int) terms.stream().filter(pageTerms::contains).count();
                    return new ScoredPage(page, score);
                })
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredPage::score).reversed())
                .toList();

        List<BookPage> chosen = new ArrayList<>();
        for (int offset = 0; offset < ranked.size() && chosen.size() < limit;) {
            int score = ranked.get(offset).score();
            List<BookPage> sameScore = new ArrayList<>();
            while (offset < ranked.size() && ranked.get(offset).score() == score) {
                sameScore.add(ranked.get(offset++).page());
            }
            sameScore.sort(Comparator.comparingInt(BookPage::getPageNumber));
            chosen.addAll(sampleAcrossBook(sameScore, Math.min(limit - chosen.size(), sameScore.size())));
        }
        return chosen;
    }

    private Set<String> searchTerms(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\u064B-\u065F\u0670\u0640]", "")
                .replaceAll("[أإآٱ]", "ا").replace('ى', 'ي').replace('ة', 'ه')
                .replaceAll("[^\\p{L}\\p{N}]+", " ");
        Set<String> terms = new HashSet<>();
        for (String word : normalized.split("\\s+")) {
            if (word.length() < 3 || SEARCH_STOPWORDS.contains(word)) continue;
            if (word.startsWith("وال") && word.length() > 6) word = word.substring(3);
            else if (word.startsWith("ال") && word.length() > 5) word = word.substring(2);
            terms.add(word);
        }
        return terms;
    }

    private List<BookPage> selectDiversePages(List<BookPage> candidates, int limit, int bookSize) {
        List<BookPage> chosen = new ArrayList<>();
        int minGap = Math.max(2, bookSize / 20);
        for (BookPage page : candidates) {
            if (chosen.size() >= limit) break;
            if (chosen.stream().noneMatch(existing ->
                    Math.abs(existing.getPageNumber() - page.getPageNumber()) < minGap)) {
                chosen.add(page);
            }
        }
        for (BookPage page : candidates) {
            if (chosen.size() >= limit) break;
            if (!chosen.contains(page)) chosen.add(page);
        }
        return chosen;
    }

    private List<BookPage> sampleAcrossBook(List<BookPage> pages, int limit) {
        if (limit <= 0 || pages.isEmpty()) return List.of();
        if (limit == 1) return List.of(pages.get(pages.size() / 2));
        if (pages.size() <= limit) return pages;
        List<BookPage> sample = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int index = (int) Math.round((double) i * (pages.size() - 1) / (limit - 1));
            sample.add(pages.get(index));
        }
        return sample;
    }

    private boolean hasReaderText(BookPage page) {
        String text = page.getMarkdownClean() != null && !page.getMarkdownClean().isBlank()
                ? page.getMarkdownClean() : page.getMarkdownContent();
        return text != null && !text.isBlank();
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
