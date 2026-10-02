package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.text.ArabicNumberUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spec step 9: "title ....... ١٢" or (right-to-left extraction) "١٢ . . . .
 * title" into title + printed page.
 */
@Component
public class PrintedTocParser {

    private static final String SEP = "[\\s.…·_\\-–—•*\\uFFFD]";
    private static final Pattern TITLE_THEN_NUMBER = Pattern.compile("^(.+?)" + SEP + "{2,}(\\d{1,4})$");
    private static final Pattern TITLE_THEN_NUMBER_THEN_LEADERS = Pattern.compile("^(.+?)\\s+(\\d{1,4})" + SEP + "{2,}$");
    private static final Pattern NUMBER_THEN_TITLE = Pattern.compile("^(\\d{1,4})(" + SEP + "+)(.+)$");
    private static final Pattern TITLE_SPACE_NUMBER = Pattern.compile("^(.+?)\\s+(\\d{1,4})$");
    private static final int MAX_TITLE_CHARS = 180;
    private static final int MAX_TITLE_WORDS = 25;

    public List<RawEntry> parse(List<PageContent> tocPages) {
        List<RawEntry> entries = new ArrayList<>();
        String pendingTitle = null;
        for (PageContent page : tocPages) {
            for (String rawLine : page.lines()) {
                if (rawLine == null) continue;
                String line = rawLine.strip();
                if (line.isBlank()) {
                    continue;
                }
                Optional<RawEntry> direct = parseLine(line, true, page.pdfPage());
                if (direct.isPresent()) {
                    entries.add(direct.get());
                    pendingTitle = null;
                    continue;
                }
                if (pendingTitle != null) {
                    String combined = pendingTitle + " " + line;
                    Optional<RawEntry> combinedEntry = parseLine(combined, true, page.pdfPage());
                    if (combinedEntry.isPresent()) {
                        entries.add(combinedEntry.get());
                        pendingTitle = null;
                        continue;
                    }
                }
                if (line.chars().filter(Character::isLetter).count() >= 2 && line.length() <= 100) {
                    pendingTitle = line;
                } else {
                    pendingTitle = null;
                }
            }
        }
        return entries;
    }

    public static Optional<RawEntry> parseLine(String line) {
        return parseLine(line, false, null);
    }

    public static Optional<RawEntry> parseLine(String line, boolean onTocPage, Integer currentPagePdf) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String original = line.replaceAll("[\\x00-\\x1F\\x7F]", " ").strip();
        String ascii = ArabicNumberUtils.toAsciiDigits(original); // same length: indexes line up with the original
        Matcher m = TITLE_THEN_NUMBER.matcher(ascii);
        if (m.matches()) {
            return entry(original.substring(0, m.end(1)), Integer.parseInt(m.group(2)), false, currentPagePdf);
        }
        m = TITLE_THEN_NUMBER_THEN_LEADERS.matcher(ascii);
        if (m.matches()) {
            return entry(original.substring(0, m.end(1)), Integer.parseInt(m.group(2)), false, currentPagePdf);
        }
        m = NUMBER_THEN_TITLE.matcher(ascii);
        if (m.matches()) {
            String sepGroup = m.group(2);
            // If separator contains hyphen/dash like "1 - ", it's a list bullet, not dot leaders.
            // Also if line ends with a number (e.g. date prefix or chapter number with page at the end), skip leading number.
            if (!sepGroup.matches(".*[-–—].*") && !m.group(3).matches(".*\\s+\\d{1,4}$")) {
                boolean leaders = sepGroup.replaceAll("[\\s\\-–—]", "").length() >= 2 || sepGroup.length() >= 4;
                return entry(original.substring(m.start(3)), Integer.parseInt(m.group(1)), !onTocPage && !leaders, currentPagePdf);
            }
        }
        m = TITLE_SPACE_NUMBER.matcher(ascii);
        if (m.matches()) {
            return entry(original.substring(0, m.end(1)), Integer.parseInt(m.group(2)), !onTocPage, currentPagePdf);
        }
        return Optional.empty();
    }

    private static Optional<RawEntry> entry(String rawTitle, int printedPage, boolean needsKnownHeading, Integer currentPagePdf) {
        String title = rawTitle.replaceAll("^" + SEP + "+|" + SEP + "+$", "").replace("\uFFFD", "").strip();
        if (currentPagePdf != null && printedPage == currentPagePdf && !HeadingWords.isKnownHeading(title)) {
            return Optional.empty();
        }
        long letters = title.chars().filter(Character::isLetter).count();
        if (letters < 2 || title.length() > MAX_TITLE_CHARS || title.split("\\s+").length > MAX_TITLE_WORDS) {
            return Optional.empty();
        }
        if (needsKnownHeading && !HeadingWords.isKnownHeading(title)) {
            return Optional.empty();
        }
        return Optional.of(new RawEntry(title, HeadingWords.levelOf(title), null, printedPage));
    }
}
