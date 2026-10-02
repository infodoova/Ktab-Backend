package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.PageContent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spec step 11, last resort. A line is a heading only when it is a known division/special heading <em>and</em> sits near
 * the top of its page, is short, stands alone and is followed by substantial text. A mention inside a paragraph is not.
 * (PDF font sizes are not used in V1.)
 */
@Component
public class HeadingDetector {

    private static final int TOP_LINES = 6;
    private static final int MAX_CHARS = 60;
    private static final int MIN_FOLLOWING_CHARS = 200;

    public List<RawEntry> detect(List<PageContent> pages) {
        List<RawEntry> found = new ArrayList<>();
        String lastTitle = null;
        int lastPage = -10;
        for (int pi = 0; pi < pages.size(); pi++) {
            PageContent page = pages.get(pi);
            if (page.imageOnly() || page.cleanedText().isBlank()) {
                continue;
            }
            String[] lines = page.cleanedText().split("\n", -1);
            int pos = 0;
            for (int li = 0; li < lines.length && pos < TOP_LINES; li++) {
                String line = lines[li].strip();
                if (line.isEmpty()) {
                    continue;
                }
                boolean alone = pos == 0 || lines[li - 1].isBlank();
                if (alone && line.length() <= MAX_CHARS && !line.matches(".*[.،؛:]$")
                        && PrintedTocParser.parseLine(line).isEmpty() && HeadingWords.isHeadingLine(line)
                        && followingChars(pages, pi, lines, li) >= MIN_FOLLOWING_CHARS) {
                    String key = HeadingWords.norm(line);
                    boolean runningHeaderRepeat = key.equals(lastTitle) && page.pdfPage() == lastPage + 1;
                    if (!runningHeaderRepeat) {
                        found.add(new RawEntry(line, HeadingWords.levelOf(line), page.pdfPage(), null));
                    }
                    lastTitle = key;
                    lastPage = page.pdfPage();
                }
                pos++;
            }
        }
        return found;
    }

    private static int followingChars(List<PageContent> pages, int pageIndex, String[] lines, int lineIndex) {
        int n = 0;
        for (int i = lineIndex + 1; i < lines.length; i++) {
            n += lines[i].replaceAll("\\s+", "").length();
        }
        if (pageIndex + 1 < pages.size()) {
            n += pages.get(pageIndex + 1).cleanedText().replaceAll("\\s+", "").length();
        }
        return n;
    }
}
