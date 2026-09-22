package com.doova.ktab.features.ocr.text;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility for normalizing Arabic text strictly for fuzzy matching, classification,
 * and search. NEVER use normalized text to overwrite stored display text.
 */
public final class ArabicTextNormalizer {

    private static final Pattern TASHKEEL_TATWEEL_PATTERN = Pattern.compile("[\\u064B-\\u0652\\u0670\\u0640]");
    private static final Pattern LEADER_DOTS_PUNCTUATION = Pattern.compile("[\\.\\-\\–\\—\\_\\:\\,\\،\\؛\\؟\\?\\!\\(\\)\\[\\]\\{\\}\\/\\\\\"\\'«»“”]+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private ArabicTextNormalizer() {
        // Utility class
    }

    /**
     * Normalizes Arabic text by:
     * - Stripping tashkeel (diacritics) and tatweel (kashida)
     * - Normalizing hamza forms (أ إ آ ٱ -> ا)
     * - Normalizing alif maqsura (ى -> ي)
     * - Normalizing ta marbuta (ة -> ه)
     * - Normalizing waw with hamza (ؤ -> و) and ya with hamza (ئ -> ي)
     * - Converting Arabic-Indic (٠-٩) and Persian (۰-۹) digits to ASCII (0-9)
     * - Removing leader dots, punctuation, and collapsing whitespace
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }

        // 1. Convert digits (Arabic-Indic & Persian to ASCII)
        String s = convertDigitsToAscii(text);

        // 2. Strip diacritics and tatweel
        s = TASHKEEL_TATWEEL_PATTERN.matcher(s).replaceAll("");

        // 3. Normalize letter variants
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\u0622': // آ
                case '\u0623': // أ
                case '\u0625': // إ
                case '\u0671': // ٱ
                    sb.append('\u0627'); // ا
                    break;
                case '\u0649': // ى
                case '\u0626': // ئ
                    sb.append('\u064A'); // ي
                    break;
                case '\u0629': // ة
                    sb.append('\u0647'); // ه
                    break;
                case '\u0624': // ؤ
                    sb.append('\u0648'); // و
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        s = sb.toString();

        // 4. Strip punctuation and leader dots
        s = LEADER_DOTS_PUNCTUATION.matcher(s).replaceAll(" ");

        // 5. Trim and collapse whitespace
        return WHITESPACE_PATTERN.matcher(s).replaceAll(" ").trim();
    }

    /**
     * Converts Arabic-Indic (٠-٩) and Persian (۰-۹) digits to ASCII (0-9).
     */
    public static String convertDigitsToAscii(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') { // Arabic-Indic digits ٠-٩
                sb.append((char) ('0' + (c - '\u0660')));
            } else if (c >= '\u06F0' && c <= '\u06F9') { // Extended Arabic-Indic / Persian digits ۰-۹
                sb.append((char) ('0' + (c - '\u06F0')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Computes fuzzy token-set similarity between two strings after normalization.
     * Returns a score between 0.0 (no match) and 1.0 (exact match).
     */
    public static double similarity(String s1, String s2) {
        String n1 = normalize(s1);
        String n2 = normalize(s2);

        if (n1.isEmpty() && n2.isEmpty()) return 1.0;
        if (n1.isEmpty() || n2.isEmpty()) return 0.0;
        if (n1.equals(n2)) return 1.0;

        Set<String> tokens1 = new HashSet<>(Arrays.asList(n1.split(" ")));
        Set<String> tokens2 = new HashSet<>(Arrays.asList(n2.split(" ")));

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        if (union.isEmpty()) return 0.0;

        double jaccard = (double) intersection.size() / union.size();

        // Also check if one string contains the other (e.g. truncated title)
        if (n1.contains(n2) || n2.contains(n1)) {
            double containment = (double) Math.min(n1.length(), n2.length()) / Math.max(n1.length(), n2.length());
            return Math.max(jaccard, Math.max(containment, 0.85));
        }

        return jaccard;
    }
}
