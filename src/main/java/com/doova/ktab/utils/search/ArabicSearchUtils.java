package com.doova.ktab.utils.search;

import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance linguistic utilities for Arabic and multilingual full-text search.
 * Handles diacritics stripping (tashkeel), hamza normalization, tatweel removal,
 * digit unification, and contextual snippet extraction with highlight markers.
 */
@UtilityClass
public class ArabicSearchUtils {

    // Tashkeel / Harakat + Tatweel regex: Fathatan..Sukun, Superscript Alef, Tatweel
    private static final Pattern DIACRITICS_AND_TATWEEL_PATTERN =
            Pattern.compile("[\u064B-\u0652\u0670\u0640]");

    // Alef variations: أ, إ, آ, ٱ -> ا
    private static final Pattern ALEF_VARIATIONS_PATTERN =
            Pattern.compile("[\u0622\u0623\u0625\u0671]");

    // Taa Marbuta: ة -> ه
    private static final Pattern TAA_MARBUTA_PATTERN =
            Pattern.compile("\u0629");

    // Alef Maksura: ى -> ي
    private static final Pattern ALEF_MAKSURA_PATTERN =
            Pattern.compile("\u0649");

    // Multiple consecutive whitespace characters
    private static final Pattern MULTI_WHITESPACE_PATTERN =
            Pattern.compile("\\s+");

    private static final char[] EASTERN_ARABIC_DIGITS = {
            '٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩'
    };

    /**
     * Normalizes text for search comparison:
     * - Strips tashkeel (diacritics) & tatweel
     * - Normalizes Alef forms (أ, إ, آ, ٱ -> ا)
     * - Normalizes Taa Marbuta (ة -> ه)
     * - Normalizes Alef Maksura (ى -> ي)
     * - Converts Eastern-Arabic digits (٠-٩) to standard ASCII (0-9)
     * - Trims and unifies whitespace
     * - Converts English characters to lower-case
     *
     * @param text input string
     * @return normalized string, or empty string if input was null/blank
     */
    public static String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        String normalized = text.trim();

        // 1. Strip Tashkeel (harakat) and Tatweel
        normalized = DIACRITICS_AND_TATWEEL_PATTERN.matcher(normalized).replaceAll("");

        // 2. Normalize Alef variants to simple bare Alef 'ا'
        normalized = ALEF_VARIATIONS_PATTERN.matcher(normalized).replaceAll("\u0627");

        // 3. Normalize Taa Marbuta to Haa 'ه'
        normalized = TAA_MARBUTA_PATTERN.matcher(normalized).replaceAll("\u0647");

        // 4. Normalize Alef Maksura to Yaa 'ي'
        normalized = ALEF_MAKSURA_PATTERN.matcher(normalized).replaceAll("\u064A");

        // 5. Eastern Arabic digits to ASCII digits
        char[] chars = normalized.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c >= '٠' && c <= '٩') {
                chars[i] = (char) ('0' + (c - '٠'));
            }
        }
        normalized = new String(chars);

        // 6. Lowercase English and consolidate whitespace
        normalized = MULTI_WHITESPACE_PATTERN.matcher(normalized.toLowerCase()).replaceAll(" ");

        return normalized.trim();
    }

    /**
     * Builds a safe SQL LIKE pattern with normalized wildcards (%term%).
     * Escapes SQL special characters % and _ in the user input.
     *
     * @param rawQuery user input term
     * @return escaped, normalized query surrounded by % wildcards
     */
    public static String toNormalizedLikePattern(String rawQuery) {
        if (!StringUtils.hasText(rawQuery)) {
            return "%";
        }
        String normalized = normalize(rawQuery);
        // Escape existing SQL LIKE characters
        String escaped = normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Extracts a contextual snippet of text surrounding the first match of the search query.
     * Surrounds the matched segment with [[HIGHLIGHT]] and [[/HIGHLIGHT]] tags.
     *
     * @param content full text content (e.g. book page markdown)
     * @param query search term
     * @param windowSize total character size around the match
     * @return highlighted preview snippet
     */
    public static String extractSnippet(String content, String query, int windowSize) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(query)) {
            return "";
        }

        // Clean markdown symbols for cleaner snippet
        String cleanContent = content.replaceAll("[#*`_~>\\[\\]]", " ");
        cleanContent = MULTI_WHITESPACE_PATTERN.matcher(cleanContent).replaceAll(" ").trim();

        String normalizedContent = normalize(cleanContent);
        String normalizedQuery = normalize(query);

        int matchIndex = normalizedContent.indexOf(normalizedQuery);
        if (matchIndex == -1) {
            // Fallback: return first windowSize characters
            int end = Math.min(cleanContent.length(), windowSize);
            return cleanContent.substring(0, end) + (cleanContent.length() > windowSize ? "..." : "");
        }

        int halfWindow = windowSize / 2;
        int start = Math.max(0, matchIndex - halfWindow);
        int end = Math.min(cleanContent.length(), matchIndex + query.length() + halfWindow);

        // Find clean word boundaries if possible
        if (start > 0) {
            int spaceIndex = cleanContent.indexOf(' ', start);
            if (spaceIndex != -1 && spaceIndex < matchIndex) {
                start = spaceIndex + 1;
            }
        }
        if (end < cleanContent.length()) {
            int spaceIndex = cleanContent.lastIndexOf(' ', end);
            if (spaceIndex != -1 && spaceIndex > matchIndex + query.length()) {
                end = spaceIndex;
            }
        }

        String snippet = cleanContent.substring(start, end);
        String matchedPortion = cleanContent.substring(matchIndex, Math.min(cleanContent.length(), matchIndex + query.length()));

        // Insert highlight tags
        int relativeMatch = matchIndex - start;
        if (relativeMatch >= 0 && relativeMatch + query.length() <= snippet.length()) {
            snippet = snippet.substring(0, relativeMatch)
                    + "[[HIGHLIGHT]]"
                    + snippet.substring(relativeMatch, relativeMatch + query.length())
                    + "[[/HIGHLIGHT]]"
                    + snippet.substring(relativeMatch + query.length());
        }

        String prefix = (start > 0) ? "..." : "";
        String suffix = (end < cleanContent.length()) ? "..." : "";

        return prefix + snippet + suffix;
    }
}
