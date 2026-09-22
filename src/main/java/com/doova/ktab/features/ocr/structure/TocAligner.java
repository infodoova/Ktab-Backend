package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PaginationMode;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;
import com.doova.ktab.features.ocr.text.PageLabelParser;
import com.doova.ktab.model.book.BookPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class TocAligner {

    private final OcrProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record AlignedSection(
            String title,
            String divisionLabel,
            Integer ordinal,
            int level,
            String printedLabel,
            com.doova.ktab.enums.book.SectionType sectionType,
            int startPage,
            String startAnchor,
            BigDecimal confidence,
            boolean needsReview
    ) {}

    public record HeadingCandidate(int bookPage, String text, int levelHint) {}

    /**
     * Aligns TOC entries to book pages and detected headings.
     */
    public List<AlignedSection> align(
            RawToc rawToc,
            List<BookPage> pages,
            PageOffsetResolver offsetResolver,
            PaginationMode paginationMode
    ) {
        if (rawToc == null || rawToc.entries().isEmpty()) {
            return List.of();
        }

        // Map pages by pageNumber
        Map<Integer, BookPage> pageMap = new HashMap<>();
        for (BookPage p : pages) {
            pageMap.put(p.getPageNumber(), p);
        }

        // Extract all detected headings across all pages
        List<HeadingCandidate> allHeadings = new ArrayList<>();
        for (BookPage page : pages) {
            extractHeadingsFromPage(page, allHeadings);
        }

        List<AlignedSection> aligned = new ArrayList<>();
        int prevStartPage = 1;

        int searchWindow = properties.getStructure().getSearchWindow();
        double matchThreshold = properties.getStructure().getMatchThreshold();

        boolean isNoneMode = (paginationMode == PaginationMode.NONE);

        for (RawToc.RawTocEntry entry : rawToc.entries()) {
            Optional<Integer> printedNum = PageLabelParser.parseNumeric(entry.printedPageLabel());

            Integer candidatePage = null;
            if (entry.targetPdfPage() != null) {
                // From PDF outline bookmark
                candidatePage = mapPdfPageToBookPage(pages, entry.targetPdfPage());
            } else if (printedNum.isPresent() && !isNoneMode) {
                candidatePage = offsetResolver.toPageIndex(printedNum.get()).orElse(null);
            }

            if (candidatePage != null) {
                // Windowed fuzzy search on headings
                int windowStart = Math.max(1, candidatePage - searchWindow);
                int windowEnd = Math.min(pages.size(), candidatePage + searchWindow);

                HeadingCandidate bestMatch = null;
                double bestScore = 0.0;

                for (HeadingCandidate hc : allHeadings) {
                    if (hc.bookPage >= windowStart && hc.bookPage <= windowEnd) {
                        double score = ArabicTextNormalizer.similarity(entry.title(), hc.text);
                        if (score >= matchThreshold && score > bestScore) {
                            bestScore = score;
                            bestMatch = hc;
                        }
                    }
                }

                if (bestMatch != null) {
                    // Snapped to heading
                    int matchedPage = bestMatch.bookPage;
                    boolean outOfOrder = matchedPage < prevStartPage;
                    aligned.add(new AlignedSection(
                            entry.title(), entry.divisionLabel(), entry.ordinal(), entry.level(),
                            entry.printedPageLabel(), entry.sectionType(),
                            matchedPage, bestMatch.text,
                            BigDecimal.valueOf(0.95), outOfOrder
                    ));
                    prevStartPage = Math.max(prevStartPage, matchedPage);
                } else {
                    // Keep candidate page without exact anchor
                    boolean outOfOrder = candidatePage < prevStartPage;
                    aligned.add(new AlignedSection(
                            entry.title(), entry.divisionLabel(), entry.ordinal(), entry.level(),
                            entry.printedPageLabel(), entry.sectionType(),
                            candidatePage, null,
                            BigDecimal.valueOf(0.70), true
                    ));
                    prevStartPage = Math.max(prevStartPage, candidatePage);
                }
            } else {
                // No printed page or NONE mode: dynamic monotonic sequence alignment
                AlignedSection fallbackSection = alignBySequence(entry, allHeadings, prevStartPage, matchThreshold);
                aligned.add(fallbackSection);
                prevStartPage = Math.max(prevStartPage, fallbackSection.startPage());
            }
        }

        return aligned;
    }

    private AlignedSection alignBySequence(
            RawToc.RawTocEntry entry,
            List<HeadingCandidate> allHeadings,
            int minPage,
            double threshold
    ) {
        HeadingCandidate best = null;
        double bestScore = 0.0;

        for (HeadingCandidate hc : allHeadings) {
            if (hc.bookPage >= minPage) {
                double score = ArabicTextNormalizer.similarity(entry.title(), hc.text);
                if (score >= threshold && score > bestScore) {
                    bestScore = score;
                    best = hc;
                }
            }
        }

        double confidenceCap = properties.getStructure().getNoPaginationConfidenceCap();

        if (best != null) {
            return new AlignedSection(
                    entry.title(), entry.divisionLabel(), entry.ordinal(), entry.level(),
                    entry.printedPageLabel(), entry.sectionType(),
                    best.bookPage, best.text,
                    BigDecimal.valueOf(Math.min(confidenceCap, 0.85)), false
            );
        }

        return new AlignedSection(
                entry.title(), entry.divisionLabel(), entry.ordinal(), entry.level(),
                entry.printedPageLabel(), entry.sectionType(),
                minPage, null,
                BigDecimal.valueOf(0.50), true
        );
    }

    private Integer mapPdfPageToBookPage(List<BookPage> pages, int pdfPage) {
        for (BookPage p : pages) {
            if (p.getSourcePdfPage() != null && p.getSourcePdfPage() == pdfPage) {
                return p.getPageNumber();
            }
        }
        return pdfPage;
    }

    private void extractHeadingsFromPage(BookPage page, List<HeadingCandidate> target) {
        if (page.getRunningHeader() != null && !page.getRunningHeader().isBlank()) {
            target.add(new HeadingCandidate(page.getPageNumber(), page.getRunningHeader(), 1));
        }

        if (page.getHeadings() != null && !page.getHeadings().isBlank()) {
            try {
                List<Map<String, Object>> list = objectMapper.readValue(page.getHeadings(), new TypeReference<>() {});
                for (Map<String, Object> map : list) {
                    String text = (String) map.get("text");
                    Number level = (Number) map.get("levelHint");
                    if (text != null && !text.isBlank()) {
                        target.add(new HeadingCandidate(page.getPageNumber(), text, level != null ? level.intValue() : 1));
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
