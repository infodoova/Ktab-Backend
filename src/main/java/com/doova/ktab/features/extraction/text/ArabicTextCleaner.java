package com.doova.ktab.features.extraction.text;

import com.doova.ktab.features.extraction.dto.BookMetadata;
import com.doova.ktab.features.extraction.dto.PageContent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Spec step 4. Removes repeated running headers/footers, repeated title/author lines, the standalone page number and
 * obvious artifacts, whole lines at a time. It never changes a letter or a diacritic and never overwrites rawText.
 */
@Component
public class ArabicTextCleaner {

    private static final double CHROME_PAGE_RATIO = 0.40;
    private static final int CHROME_MIN_PAGES = 3;
    private static final int EDGE_LINES = 2;
    private static final Pattern DIGITS = Pattern.compile("[0-9\\u0660-\\u0669\\u06F0-\\u06F9]+");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern BLANK_RUNS = Pattern.compile("\\n{3,}");
    /** The same tashkeel mark twice in a row, which no Arabic text has: fake-bold overprinting in the source PDF. */
    private static final Pattern DUPLICATE_MARK = Pattern.compile("([\\u064B-\\u0652\\u0670])\\1+");

    public List<PageContent> clean(List<PageContent> pages, BookMetadata metadata) {
        Set<String> chrome = repeatedEdgeLines(pages);
        List<PageContent> cleaned = new ArrayList<>();
        for (PageContent page : pages) {
            cleaned.add(cleanPage(page, chrome));
        }
        return cleaned;
    }

    private PageContent cleanPage(PageContent page, Set<String> chrome) {
        if (page.imageOnly() || page.lines().isEmpty()) {
            return new PageContent(page.pdfPage(), page.rawText(), "", null, null, page.lines(), page.imageOnly());
        }
        List<String> lines = page.lines();
        List<Integer> content = nonBlankIndexes(lines);
        Set<Integer> remove = new HashSet<>();
        String header = null;
        String label = null;

        for (int pos = 0; pos < content.size(); pos++) {
            boolean atEdge = pos < EDGE_LINES || pos >= content.size() - EDGE_LINES;
            int idx = content.get(pos);
            String line = lines.get(idx).strip();
            if (isArtifactLine(line)) {
                remove.add(idx);
            } else if (atEdge && ArabicNumberUtils.parsePageNumber(line).isPresent() && label == null) {
                label = line;
                remove.add(idx);
            } else if (atEdge && chrome.contains(edgeKey(line))) {
                if (header == null && pos < EDGE_LINES) {
                    header = line;
                }
                remove.add(idx);
            }
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (!remove.contains(i)) {
                text.append(lines.get(i)).append('\n');
            }
        }
        return new PageContent(page.pdfPage(), page.rawText(), collapseDuplicateMarks(collapseWhitespace(text.toString())), label, header,
                page.lines(), false);
    }

    /** Lines that sit at the top or bottom edge of at least 40% of the pages (minimum 3): running headers and footers. */
    private Set<String> repeatedEdgeLines(List<PageContent> pages) {
        Map<String, Integer> counts = new HashMap<>();
        int textPages = 0;
        for (PageContent page : pages) {
            if (page.imageOnly() || page.lines().isEmpty()) {
                continue;
            }
            textPages++;
            List<Integer> content = nonBlankIndexes(page.lines());
            Set<String> onThisPage = new HashSet<>();
            for (int pos = 0; pos < content.size(); pos++) {
                if (pos < EDGE_LINES || pos >= content.size() - EDGE_LINES) {
                    String line = page.lines().get(content.get(pos)).strip();
                    if (ArabicNumberUtils.parsePageNumber(line).isEmpty()) {
                        onThisPage.add(edgeKey(line));
                    }
                }
            }
            onThisPage.forEach(k -> counts.merge(k, 1, Integer::sum));
        }
        int threshold = Math.max(CHROME_MIN_PAGES, (int) Math.ceil(CHROME_PAGE_RATIO * textPages));
        Set<String> chrome = new HashSet<>();
        counts.forEach((k, v) -> {
            if (v >= threshold && !k.isBlank()) {
                chrome.add(k);
            }
        });
        return chrome;
    }

    /** Digits become '#' so "فصل ٣" and "فصل ٤" repeat as one running header. */
    private static String edgeKey(String line) {
        return DIGITS.matcher(SPACES.matcher(line.strip()).replaceAll(" ")).replaceAll("#");
    }

    private static List<Integer> nonBlankIndexes(List<String> lines) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                idx.add(i);
            }
        }
        return idx;
    }

    /**
     * Collapses an identical diacritic repeated back to back ("جوًًا" becomes "جوًا"). Many publisher PDFs draw each mark
     * twice; leaving both makes the text (and a voice reading it) wrong. Different marks and letters are never touched.
     */
    public static String collapseDuplicateMarks(String text) {
        return text == null ? null : DUPLICATE_MARK.matcher(text).replaceAll("$1");
    }

    /** Only runs of spaces and blank lines change: letters and diacritics are untouched. */
    public static String collapseWhitespace(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : SPACES.matcher(text).replaceAll(" ").split("\n", -1)) {
            out.append(line.strip()).append('\n');
        }
        return BLANK_RUNS.matcher(out).replaceAll("\n\n").strip();
    }

    /** A line made only of replacement characters, control characters and filler punctuation. */
    public static boolean isArtifactLine(String line) {
        String s = line.strip();
        if (s.length() < 3) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean filler = c == '\uFFFD' || Character.isISOControl(c) || c == '.' || c == '·' || c == '_' || c == '-'
                    || c == '—' || c == '–' || c == '•' || c == '*' || c == '…' || c == ' ' || c == '\u00A0';
            if (!filler) {
                return false;
            }
        }
        return true;
    }
}
