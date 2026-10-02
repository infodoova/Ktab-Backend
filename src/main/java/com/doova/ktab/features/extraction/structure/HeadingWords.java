package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The Arabic division words and what level each implies. All comparisons use
 * ArabicTextNormalizer (no diacritics, ة→ه).
 */
final class HeadingWords {

    private static final Set<String> LEVEL_1 = Set.of(
            "الفصل", "الباب", "القسم", "الجزء", "الكتاب", "الملحق", "ملحق", "مدخل");
    private static final Set<String> LEVEL_2 = Set.of("المبحث");
    private static final Set<String> LEVEL_3 = Set.of("المطلب", "الفرع");
    private static final Set<String> SPECIAL = Set.of("المقدمه", "مقدمه", "مقدمه المؤلف", "مقدمه الكتاب", "تمهيد",
            "الخاتمه", "خاتمه", "التمهيد");
    private static final Pattern DIVISION_LINE = Pattern
            .compile("^(الفصل|الباب|القسم|الجزء|المبحث|المطلب)\\s+\\S+(\\s+\\S+){0,6}$");

    private static final Set<String> EN_LEVEL_1 = Set.of(
            "chapter", "part", "book", "section", "annex", "appendix", "appendices");
    private static final Set<String> EN_SPECIAL = Set.of(
            "preface", "introduction", "conclusion", "table of contents", "contents");
    private static final Pattern EN_DIVISION_LINE = Pattern
            .compile("^(chapter|part|section|book|annex|appendix)\\s+\\S+(\\s+\\S+){0,6}$", Pattern.CASE_INSENSITIVE);

    private HeadingWords() {
    }

    static String norm(String s) {
        return ArabicTextNormalizer.normalize(s == null ? "" : s).toLowerCase().strip();
    }

    private static String firstWord(String normalized) {
        int sp = normalized.indexOf(' ');
        return sp < 0 ? normalized : normalized.substring(0, sp);
    }

    /**
     * True when the title starts with a division word or is one of the special
     * headings (المقدمة, الخاتمة, تمهيد...).
     */
    static boolean isKnownHeading(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String n = norm(title);
        String first = firstWord(n);
        if (LEVEL_1.contains(first) || LEVEL_2.contains(first) || LEVEL_3.contains(first) || SPECIAL.contains(n)
                || SPECIAL.contains(first)) {
            return true;
        }
        String lower = title.toLowerCase().trim();
        String firstLower = firstWord(lower);
        return EN_LEVEL_1.contains(firstLower) || EN_SPECIAL.contains(lower) || EN_SPECIAL.contains(firstLower);
    }

    /**
     * True when the whole line looks like a heading: a short division line, or
     * exactly a special heading.
     */
    static boolean isHeadingLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String n = norm(line);
        if (SPECIAL.contains(n) || DIVISION_LINE.matcher(n).matches()) {
            return true;
        }
        String lower = line.toLowerCase().trim();
        String firstLower = firstWord(lower);
        return EN_SPECIAL.contains(lower) || EN_SPECIAL.contains(firstLower) || EN_DIVISION_LINE.matcher(lower).matches();
    }

    static int levelOf(String title) {
        if (title == null || title.isBlank()) return 1;
        String n = norm(title);
        String first = firstWord(n);

        if (LEVEL_3.contains(first)) return 3;
        if (LEVEL_2.contains(first)) return 2;
        if (LEVEL_1.contains(first)) return 1;

        // Arabic letter prefix: أ. / ب. / ج. / د. at start → sub-section (level 2)
        if (n.matches("^[أابتثجحخدذرزسشصضطظعغفقكلمنهوي]\\s*[.)،].*")) return 2;

        // English: "Chapter ..." or Roman numeral lines → level 2
        String lower = title.trim().toLowerCase();
        String firstLower = firstWord(lower);
        if ("chapter".equals(firstLower)) return 1;
        if ("part".equals(firstLower) || "section".equals(firstLower)
                || "book".equals(firstLower) || "annex".equals(firstLower)
                || "appendix".equals(firstLower) || "appendices".equals(firstLower)) return 1;

        return 1;
    }
}
