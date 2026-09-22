package com.doova.ktab.features.ocr.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RepetitionDetectorTest {

    private final RepetitionDetector detector = new RepetitionDetector();

    @Test
    @DisplayName("hasRepetition should detect line repeated N times")
    void hasRepetition_lineRepeatedNTimes_returnsTrue() {
        String input = """
                هذا سطر عادي.
                نفس السطر المتكرر هنا
                نفس السطر المتكرر هنا
                نفس السطر المتكرر هنا
                نفس السطر المتكرر هنا
                نفس السطر المتكرر هنا
                سطر آخر.
                """;
        assertTrue(detector.hasRepetition(input, 5));
    }

    @Test
    @DisplayName("hasRepetition should return false for normal text with natural variations")
    void hasRepetition_normalText_returnsFalse() {
        String input = """
                الحمد لله رب العالمين والصلاة والسلام على أشرف الأنبياء والمرسلين.
                وبعد فهذا كتاب نافع في بيان الأصول والقواعد المعتبرة.
                وقد قسمناه إلى أبواب وفصول مرتبة على حسب الأبواب الفقهية.
                """;
        assertFalse(detector.hasRepetition(input, 5));
    }

    @Test
    @DisplayName("hasRepetition should detect infinite token repetition loop")
    void hasRepetition_tokenRepetitionLoop_returnsTrue() {
        // Build 50 repeated phrases
        String phrase = "كلمة مكررة في الحلقة ";
        String input = phrase.repeat(25);
        assertTrue(detector.hasRepetition(input, 5));
    }
}
