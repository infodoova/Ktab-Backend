package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.extraction.dto.PageContent;
import com.doova.ktab.features.extraction.text.ArabicNumberUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spec step 9: "title ....... ١٢" or (right-to-left extraction) "١٢ . . . . title" into title + printed page. */
@Component
public class PrintedTocParser {

    private static final String SEP = "[\\s.…·_\\-–—]";
    private static final Pattern TITLE_THEN_NUMBER = Pattern.compile("^(.+?)" + SEP + "{2,}(\\d{1,4})$");
    private static final Pattern NUMBER_THEN_TITLE = Pattern.compile("^(\\d{1,4})(" + SEP + "+)(.+)$");
    private static final Pattern TITLE_SPACE_NUMBER = Pattern.compile("^(.+?)\\s(\\d{1,4})$");
    private static final int MAX_TITLE_CHARS = 120;

    public List<RawEntry> parse(List<PageContent> tocPages) {
        List<RawEntry> entries = new ArrayList<>();
        for (PageContent page : tocPages) {
            for (String line : page.lines()) {
                parseLine(line).ifPresent(entries::add);
            }
        }
        return entries;
    }

    public static Optional<RawEntry> parseLine(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String original = line.strip();
        String ascii = ArabicNumberUtils.toAsciiDigits(original); // same length: indexes line up with the original
        Matcher m = TITLE_THEN_NUMBER.matcher(ascii);
        if (m.matches()) {
            return entry(original.substring(0, m.end(1)), Integer.parseInt(m.group(2)), false);
        }
        m = NUMBER_THEN_TITLE.matcher(ascii);
        if (m.matches()) {
            boolean leaders = m.group(2).replaceAll("\\s", "").length() >= 2 || m.group(2).length() >= 2;
            return entry(original.substring(m.start(3)), Integer.parseInt(m.group(1)), !leaders);
        }
        m = TITLE_SPACE_NUMBER.matcher(ascii);
        if (m.matches()) {
            return entry(original.substring(0, m.end(1)), Integer.parseInt(m.group(2)), true);
        }
        return Optional.empty();
    }

    /** needsKnownHeading: with a single space and no dot leaders, only a recognized heading word is accepted. */
    private static Optional<RawEntry> entry(String rawTitle, int printedPage, boolean needsKnownHeading) {
        String title = rawTitle.replaceAll("^" + SEP + "+|" + SEP + "+$", "").strip();
        long letters = title.chars().filter(Character::isLetter).count();
        if (letters < 2 || title.length() > MAX_TITLE_CHARS || title.split("\\s+").length > 12) {
            return Optional.empty();
        }
        if (needsKnownHeading && !HeadingWords.isKnownHeading(title)) {
            return Optional.empty();
        }
        return Optional.of(new RawEntry(title, HeadingWords.levelOf(title), null, printedPage));
    }
}
