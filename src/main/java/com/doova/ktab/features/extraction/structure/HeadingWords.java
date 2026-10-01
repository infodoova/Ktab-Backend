package com.doova.ktab.features.extraction.structure;

import com.doova.ktab.features.ocr.text.ArabicTextNormalizer;

import java.util.Set;
import java.util.regex.Pattern;

/** The Arabic division words and what level each implies. All comparisons use ArabicTextNormalizer (no diacritics, ة→ه). */
final class HeadingWords {

    private static final Set<String> LEVEL_1 = Set.of("الفصل", "الباب", "الجزء", "القسم", "الكتاب", "ملحق", "الملحق", "مدخل");
    private static final Set<String> LEVEL_2 = Set.of("المبحث");
    private static final Set<String> LEVEL_3 = Set.of("المطلب", "الفرع");
    private static final Set<String> SPECIAL = Set.of("المقدمه", "مقدمه", "مقدمه المؤلف", "مقدمه الكتاب", "تمهيد", "الخاتمه", "خاتمه", "التمهيد");
    private static final Pattern DIVISION_LINE = Pattern.compile("^(الفصل|الباب|القسم|الجزء|المبحث|المطلب)\\s+\\S+(\\s+\\S+){0,6}$");

    private HeadingWords() {
    }

    static String norm(String s) {
        return ArabicTextNormalizer.normalize(s == null ? "" : s).strip();
    }

    private static String firstWord(String normalized) {
        int sp = normalized.indexOf(' ');
        return sp < 0 ? normalized : normalized.substring(0, sp);
    }

    /** True when the title starts with a division word or is one of the special headings (المقدمة, الخاتمة, تمهيد...). */
    static boolean isKnownHeading(String title) {
        String n = norm(title);
        String first = firstWord(n);
        return LEVEL_1.contains(first) || LEVEL_2.contains(first) || LEVEL_3.contains(first) || SPECIAL.contains(n)
                || SPECIAL.contains(first);
    }

    /** True when the whole line looks like a heading: a short division line, or exactly a special heading. */
    static boolean isHeadingLine(String line) {
        String n = norm(line);
        return SPECIAL.contains(n) || DIVISION_LINE.matcher(n).matches();
    }

    static int levelOf(String title) {
        String first = firstWord(norm(title));
        if (LEVEL_2.contains(first)) {
            return 2;
        }
        if (LEVEL_3.contains(first)) {
            return 3;
        }
        return 1;
    }
}
