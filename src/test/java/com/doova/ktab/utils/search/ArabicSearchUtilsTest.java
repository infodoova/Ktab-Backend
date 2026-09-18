package com.doova.ktab.utils.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArabicSearchUtilsTest {

    @Test
    @DisplayName("normalize_arabicDiacritics_removesTashkeelCorrectly")
    void normalize_arabicDiacritics_removesTashkeelCorrectly() {
        String input = "مُحَمَّدٌ رَسُولُ اللَّهِ";
        String normalized = ArabicSearchUtils.normalize(input);
        assertThat(normalized).isEqualTo("محمد رسول الله");
    }

    @Test
    @DisplayName("normalize_alefVariants_unifiesToBareAlef")
    void normalize_alefVariants_unifiesToBareAlef() {
        String input = "أحمد إبراهيم آمنة ٱمرأة";
        String normalized = ArabicSearchUtils.normalize(input);
        assertThat(normalized).isEqualTo("احمد ابراهيم امنه امراه");
    }

    @Test
    @DisplayName("normalize_easternArabicDigits_convertsToAscii")
    void normalize_easternArabicDigits_convertsToAscii() {
        String input = "سنة ٢٠٢٤ و ١٢ شهر";
        String normalized = ArabicSearchUtils.normalize(input);
        assertThat(normalized).isEqualTo("سنه 2024 و 12 شهر");
    }

    @Test
    @DisplayName("toNormalizedLikePattern_validInput_buildsWildcardPattern")
    void toNormalizedLikePattern_validInput_buildsWildcardPattern() {
        String input = "  كِتَابٌ  ";
        String pattern = ArabicSearchUtils.toNormalizedLikePattern(input);
        assertThat(pattern).isEqualTo("%كتاب%");
    }

    @Test
    @DisplayName("extractSnippet_matchedWord_insertsHighlightTags")
    void extractSnippet_matchedWord_insertsHighlightTags() {
        String content = "هذا النص يتضمن كلمة سحرية في منتصف الحديث لتجربة الاستخراج";
        String snippet = ArabicSearchUtils.extractSnippet(content, "سحرية", 60);
        assertThat(snippet).contains("[[HIGHLIGHT]]سحرية[[/HIGHLIGHT]]");
    }
}
