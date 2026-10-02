package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.ExtractionWarning;
import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.dto.Severity;
import com.doova.ktab.features.extraction.dto.WarningCode;
import com.doova.ktab.features.extraction.text.ArabicNumberUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec step 10: printed page != PDF page. Finds each title near the top of a body page, takes the most common
 * (pdfPage - printedPage) as the book offset, and validates every entry against it.
 */
@Component
public class PageNumberResolver {

    private static final int HEADING_LINES = 8;
    private static final int TOLERANCE = 2;

    public record Resolution(List<RawEntry> entries, int offset, double confidence, List<ExtractionWarning> warnings) {
    }

    public Resolution resolve(List<RawEntry> toc, List<PageContent> pages) {
        List<ExtractionWarning> warnings = new ArrayList<>();
        Integer[] anchors = new Integer[toc.size()];
        int previousAnchor = 0;
        for (int i = 0; i < toc.size(); i++) {
            Integer found = findHeadingPage(toc.get(i).title(), pages, previousAnchor);
            anchors[i] = found;
            if (found != null) {
                previousAnchor = found;
            }
        }
        Map<Integer, Integer> votes = new HashMap<>();
        for (int i = 0; i < toc.size(); i++) {
            if (anchors[i] != null && toc.get(i).printedPage() != null) {
                votes.merge(anchors[i] - toc.get(i).printedPage(), 1, Integer::sum);
            }
        }
        int offset;
        double fallbackConfidence = 1.0;
        if (!votes.isEmpty()) {
            offset = mode(votes);
        } else {
            Integer labelOffset = offsetFromPrintedLabels(pages);
            if (labelOffset != null) {
                offset = labelOffset;
                fallbackConfidence = 0.6;
            } else {
                offset = 0;
                fallbackConfidence = 0.3;
                warnings.add(new ExtractionWarning(WarningCode.TOC_PAGE_MISMATCH, Severity.WARNING,
                        "No TOC title could be located in the body and no page labels exist; printed pages are used as PDF pages."));
            }
        }

        List<RawEntry> resolved = new ArrayList<>();
        int matched = 0;
        for (int i = 0; i < toc.size(); i++) {
            RawEntry e = toc.get(i);
            int expected = e.printedPage() + offset;
            if (expected < 1 || expected > pages.size()) {
                warnings.add(new ExtractionWarning(WarningCode.TOC_PAGE_OUT_OF_RANGE, Severity.WARNING,
                        "TOC entry \"" + e.title() + "\" points at page " + expected + ", outside the " + pages.size() + "-page document."));
                continue;
            }
            if (anchors[i] != null && Math.abs(anchors[i] - expected) <= TOLERANCE) {
                matched++;
                resolved.add(new RawEntry(e.title(), e.level(), anchors[i], e.printedPage()));
            } else {
                if (!votes.isEmpty()) {
                    warnings.add(new ExtractionWarning(WarningCode.TOC_PAGE_MISMATCH, Severity.WARNING,
                            "Printed TOC page for \"" + e.title() + "\" could not be confidently mapped to a PDF page."));
                }
                resolved.add(new RawEntry(e.title(), e.level(), expected, e.printedPage()));
            }
        }
        double confidence = toc.isEmpty() ? 0 : (votes.isEmpty() ? fallbackConfidence : (double) matched / toc.size());
        return new Resolution(resolved, offset, confidence, warnings);
    }

    private static int mode(Map<Integer, Integer> votes) {
        int best = Integer.MAX_VALUE;
        int bestVotes = -1;
        for (Map.Entry<Integer, Integer> v : votes.entrySet()) {
            if (v.getValue() > bestVotes || (v.getValue() == bestVotes && v.getKey() < best)) {
                best = v.getKey();
                bestVotes = v.getValue();
            }
        }
        return best;
    }

    /** First page after {@code after} whose top lines contain the title as a heading (not as a TOC line). */
    private static Integer findHeadingPage(String title, List<PageContent> pages, int after) {
        String wanted = HeadingWords.norm(title);
        // Printed TOCs often carry a subtitle the page heading does not repeat: "الفصل الأول: شرق عدن" / "الجزء الأول - الربيع".
        String bare = HeadingWords.norm(title.split("\\s*[:：]\\s*|\\s+[-–—]\\s+", 2)[0]);
        for (PageContent page : pages) {
            if (page.pdfPage() <= after || page.imageOnly()) {
                continue;
            }
            int seen = 0;
            for (String line : page.cleanedText().split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                if (PrintedTocParser.parseLine(line).isEmpty()) {
                    String n = HeadingWords.norm(line);
                    boolean full = n.equals(wanted) || n.startsWith(wanted + " ");
                    boolean withoutSubtitle = !bare.isEmpty() && (n.equals(bare) || n.startsWith(bare + " "));
                    boolean headingIsStartOfTitle = HeadingWords.isHeadingLine(line) && wanted.startsWith(n + " ");
                    if (full || withoutSubtitle || headingIsStartOfTitle) {
                        return page.pdfPage();
                    }
                }
                if (++seen >= HEADING_LINES) {
                    break;
                }
            }
        }
        return null;
    }

    private static Integer offsetFromPrintedLabels(List<PageContent> pages) {
        Map<Integer, Integer> votes = new HashMap<>();
        for (PageContent p : pages) {
            if (p.printedPageLabel() != null) {
                ArabicNumberUtils.parsePageNumber(p.printedPageLabel()).ifPresent(n -> votes.merge(p.pdfPage() - n, 1, Integer::sum));
            }
        }
        return votes.isEmpty() ? null : mode(votes);
    }
}
