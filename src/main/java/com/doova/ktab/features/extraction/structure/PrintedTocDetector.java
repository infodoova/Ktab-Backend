package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.PageContent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Spec step 7: find the فهرس pages in the opening pages (the first 40 by default). */
@Component
public class PrintedTocDetector {

    private static final int SEARCH_PAGES = 40;
    private static final int HEADING_LINES = 5;
    private static final int MIN_TOC_LINES = 3;
    private static final Set<String> TITLES = Set.of("الفهرس", "فهرس المحتويات", "المحتويات", "المحتويات العامه", "فهرس");

    public List<Integer> findTocPages(List<PageContent> pages) {
        List<Integer> found = new ArrayList<>();
        int limit = Math.min(SEARCH_PAGES, pages.size());
        for (int i = 0; i < limit; i++) {
            PageContent page = pages.get(i);
            boolean continuation = !found.isEmpty() && found.get(found.size() - 1) == page.pdfPage() - 1;
            if (tocLines(page) < MIN_TOC_LINES) {
                continue;
            }
            if (continuation || hasTocTitle(page)) {
                found.add(page.pdfPage());
            }
        }
        return found;
    }

    private static int tocLines(PageContent page) {
        int n = 0;
        for (String line : page.lines()) {
            if (PrintedTocParser.parseLine(line).isPresent()) {
                n++;
            }
        }
        return n;
    }

    private static boolean hasTocTitle(PageContent page) {
        int seen = 0;
        for (String line : page.lines()) {
            if (line.isBlank()) {
                continue;
            }
            if (TITLES.contains(HeadingWords.norm(line))) {
                return true;
            }
            if (++seen >= HEADING_LINES) {
                break;
            }
        }
        return false;
    }
}
