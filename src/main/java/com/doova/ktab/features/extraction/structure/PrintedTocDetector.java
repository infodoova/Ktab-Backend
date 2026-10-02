package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.PageContent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spec step 7: find the فهرس pages in the opening pages (the first 40 by
 * default).
 */
@Component
public class PrintedTocDetector {

    private static final int SEARCH_PAGES = 40;
    private static final int HEADING_LINES = 5;
    private static final int MIN_TOC_LINES = 3;

    public List<Integer> findTocPages(List<PageContent> pages) {
        List<Integer> found = new ArrayList<>();
        int limit = Math.min(SEARCH_PAGES, pages.size());
        for (int i = 0; i < limit; i++) {
            PageContent page = pages.get(i);
            boolean continuation = isContinuation(pages, found, page);
            boolean hasTitle = hasTocTitle(page);
            boolean candidate = continuation || hasTitle;
            if (!candidate) {
                continue;
            }
            if (tocLines(page, true) >= MIN_TOC_LINES) {
                found.add(page.pdfPage());
            }
        }
        return found;
    }

    private static boolean isContinuation(List<PageContent> pages, List<Integer> found, PageContent page) {
        if (found.isEmpty()) {
            return false;
        }
        int lastPdfPage = found.get(found.size() - 1);
        if (page.pdfPage() == lastPdfPage + 1) {
            return true;
        }
        // In Arabic two-page spreads, TOC may continue on recto page with an empty/spacer verso page in between (e.g. 9 then 11)
        if (page.pdfPage() == lastPdfPage + 2) {
            for (PageContent p : pages) {
                if (p.pdfPage() == lastPdfPage + 1) {
                    return p.lines().stream().filter(l -> !l.isBlank()).count() <= 3;
                }
            }
        }
        return false;
    }

    private static int tocLines(PageContent page, boolean onCandidate) {
        int n = 0;
        for (String line : page.lines()) {
            if (PrintedTocParser.parseLine(line, onCandidate, page.pdfPage()).isPresent()) {
                n++;
            }
        }
        return n;
    }

    private static final Set<String> TITLES = Set.of(
            "الفهرس", "فهرس المحتويات", "المحتويات", "المحتويات العامه", "فهرس", "محتويات", "فهرس المحتويات العامه",
            "table of contents", "contents");

    private static boolean hasTocTitle(PageContent page) {
        int seen = 0;
        for (String rawLine : page.lines()) {
            if (rawLine == null) continue;
            String line = rawLine.replaceAll("[\\x00-\\x1F\\x7F]", " ").strip();
            if (line.isBlank()) {
                continue;
            }
            String lower = line.toLowerCase();
            if (lower.equals("table of contents") || lower.equals("contents") || lower.startsWith("table of contents")) {
                return true;
            }
            String norm = HeadingWords.norm(line);
            if (TITLES.contains(norm) || norm.equals("فهرس") || norm.startsWith("فهرس ") || norm.startsWith("المحتويات")) {
                return true;
            }
            if (++seen >= HEADING_LINES) {
                break;
            }
        }
        return false;
    }
}
