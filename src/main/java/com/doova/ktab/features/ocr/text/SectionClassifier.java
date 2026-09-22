package com.doova.ktab.features.ocr.text;

import com.doova.ktab.enums.book.SectionType;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies section headings into SectionType and extracts division labels and ordinals.
 */
public final class SectionClassifier {

    private static final Map<String, Integer> ARABIC_ORDINALS = Map.ofEntries(
            Map.entry("الاول", 1),
            Map.entry("الاولي", 1),
            Map.entry("الثاني", 2),
            Map.entry("الثانيه", 2),
            Map.entry("الثالث", 3),
            Map.entry("الثالثه", 3),
            Map.entry("الرابع", 4),
            Map.entry("الرابعه", 4),
            Map.entry("الخامس", 5),
            Map.entry("الخامسه", 5),
            Map.entry("السادس", 6),
            Map.entry("السادسه", 6),
            Map.entry("السابع", 7),
            Map.entry("السابعه", 7),
            Map.entry("الثامن", 8),
            Map.entry("الثامنه", 8),
            Map.entry("التاسع", 9),
            Map.entry("التاسعه", 9),
            Map.entry("العاشر", 10),
            Map.entry("العاشره", 10)
    );

    private static final Pattern DIVISION_PATTERN = Pattern.compile(
            "(?:^|[\\s:،,-])(الباب|الكتاب|القسم|الفصل|المبحث|المطلب|الفرع|ملحق|الملحق)(?=[\\s:،,-]|$)"
    );

    private SectionClassifier() {
        // Utility class
    }

    /**
     * Classifies a heading title into a SectionType based on keywords.
     */
    public static SectionType classify(String title) {
        if (title == null || title.isBlank()) {
            return SectionType.OTHER;
        }

        String norm = ArabicTextNormalizer.normalize(title);

        if (norm.contains("اهداء")) return SectionType.DEDICATION;
        if (norm.contains("تقديم") || norm.contains("كلمه تقديم") || norm.contains("تصدير") || norm.contains("توطئه")) return SectionType.FOREWORD;
        if (norm.contains("مقدمه") || norm.contains("تمهيد") || norm.contains("مدخل")) return SectionType.INTRODUCTION;

        if (norm.contains("الباب") || norm.contains("الكتاب") || norm.contains("القسم")) return SectionType.PART;
        if (norm.contains("الفصل")) return SectionType.CHAPTER;
        if (norm.contains("المبحث") || norm.contains("المطلب") || norm.contains("الفرع")) return SectionType.SUBSECTION;

        if (norm.contains("خاتمه")) return SectionType.CONCLUSION;
        if (norm.contains("ملحق") || norm.contains("ملاحق")) return SectionType.APPENDIX;
        if (norm.contains("مراجع") || norm.contains("مصادر") || norm.contains("ثبت المصادر")) return SectionType.BIBLIOGRAPHY;

        // Specific indices (names, verses, hadith, places)
        if (norm.contains("فهرس الاعلام") || norm.contains("فهرس الايات") ||
                norm.contains("فهرس الاحاديث") || norm.contains("فهرس الاماكن") ||
                norm.contains("كشاف")) {
            return SectionType.INDEX;
        }

        // Generic "فهرس" alone is typically FRONT_MATTER or OTHER
        if (norm.contains("فهرس")) {
            return SectionType.FRONT_MATTER;
        }

        return SectionType.OTHER;
    }

    /**
     * Extracts division label (e.g. "الفصل", "الباب", "المبحث") if present.
     */
    public static Optional<String> extractDivisionLabel(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = DIVISION_PATTERN.matcher(title);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    /**
     * Parses Arabic ordinal words (e.g. "الثالث" -> 3) or digits after division label.
     */
    public static Optional<Integer> extractOrdinal(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }

        String norm = ArabicTextNormalizer.normalize(title);

        // Check for ASCII digits
        Pattern numPattern = Pattern.compile("\\b(\\d+)\\b");
        Matcher numMatcher = numPattern.matcher(norm);
        if (numMatcher.find()) {
            try {
                return Optional.of(Integer.parseInt(numMatcher.group(1)));
            } catch (NumberFormatException ignored) {}
        }

        // Check for textual ordinals
        String[] tokens = norm.split(" ");
        for (String token : tokens) {
            if (ARABIC_ORDINALS.containsKey(token)) {
                return Optional.of(ARABIC_ORDINALS.get(token));
            }
        }

        return Optional.empty();
    }
}
