package com.doova.ktab.features.ocr.text;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses printed page labels into numeric integers or identifies them as front-matter indicators.
 */
public final class PageLabelParser {

    private static final Pattern NUMERIC_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern ROMAN_NUMERAL_PATTERN = Pattern.compile("^(?i)[ivxlcdm]+$");

    // Standard Arabic Abjad numerals for front matter (أ, ب, ج, د, هـ, و, ز, ح, ط, ي, ...)
    private static final Set<String> ABJAD_LETTERS = Set.of(
            "ا", "أ", "إ", "آ", "ب", "ت", "ث", "ج", "ح", "خ",
            "د", "ذ", "ر", "ز", "س", "ش", "ص", "ض", "ط", "ظ",
            "ع", "غ", "ف", "ق", "ك", "ل", "م", "ن", "ه", "هـ", "و", "ي"
    );

    private PageLabelParser() {
        // Utility class
    }

    /**
     * Parses a printed label into a numeric page number.
     * Converts Arabic-Indic and Persian digits to ASCII and extracts the integer.
     * Returns Optional.empty() if the label is non-numeric, abjad, or roman.
     */
    public static Optional<Integer> parseNumeric(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return Optional.empty();
        }

        String converted = ArabicTextNormalizer.convertDigitsToAscii(rawLabel.trim());
        Matcher matcher = NUMERIC_PATTERN.matcher(converted);
        if (matcher.find()) {
            try {
                int val = Integer.parseInt(matcher.group(1));
                return val > 0 ? Optional.of(val) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if the label is a front-matter indicator (abjad letter or roman numeral).
     */
    public static boolean isFrontMatter(String rawLabel) {
        if (rawLabel == null || rawLabel.isBlank()) {
            return false;
        }

        String cleaned = rawLabel.trim()
                .replaceAll("[\\.\\-\\–\\—\\_\\(\\)\\[\\]\\{\\}\\/\\\\]+", "")
                .trim();

        if (cleaned.isEmpty()) {
            return false;
        }

        // Check Arabic Abjad
        String normalized = ArabicTextNormalizer.normalize(cleaned);
        if (ABJAD_LETTERS.contains(normalized) || ABJAD_LETTERS.contains(cleaned)) {
            return true;
        }

        // Check Roman numerals
        return ROMAN_NUMERAL_PATTERN.matcher(cleaned).matches();
    }
}
