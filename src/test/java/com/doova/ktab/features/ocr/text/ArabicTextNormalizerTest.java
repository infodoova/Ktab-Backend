package com.doova.ktab.features.ocr.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArabicTextNormalizerTest {

    @Test
    @DisplayName("normalize should strip tashkeel and tatweel properly")
    void normalize_tashkeelAndTatweel_stripped() {
        String input = "الْكِتَابُ المَشْهُورُ كــــتــــاب";
        String expected = "الكتاب المشهور كتاب";
        assertEquals(expected, ArabicTextNormalizer.normalize(input));
    }

    @Test
    @DisplayName("normalize should normalize all hamza variants to plain alif")
    void normalize_hamzaVariants_normalizedToAlif() {
        String input = "أحمد إبراهيم آدم ٱستغفار";
        String expected = "احمد ابراهيم ادم استغفار";
        assertEquals(expected, ArabicTextNormalizer.normalize(input));
    }

    @Test
    @DisplayName("normalize should convert Arabic-Indic and Persian digits to ASCII")
    void normalize_arabicAndPersianDigits_convertedToAscii() {
        String input = "الصفحة ٤٥ والباب ۱۲";
        String expected = "الصفحه 45 والباب 12";
        assertEquals(expected, ArabicTextNormalizer.normalize(input));
    }

    @Test
    @DisplayName("normalize should strip leader dots and punctuation")
    void normalize_leaderDotsAndPunctuation_strippedAndTrimmed() {
        String input = "الفصل الأول: البداية..................... 15";
        String expected = "الفصل الاول البدايه 15";
        assertEquals(expected, ArabicTextNormalizer.normalize(input));
    }

    @Test
    @DisplayName("similarity should return 1.0 for identical normalized text")
    void similarity_identicalNormalizedStrings_returnsOne() {
        String s1 = "الفَصْلُ الأَوَّلُ";
        String s2 = "الفصل الاول";
        assertEquals(1.0, ArabicTextNormalizer.similarity(s1, s2), 0.001);
    }

    @Test
    @DisplayName("similarity should return high score for truncated titles")
    void similarity_truncatedTitle_returnsHighScore() {
        String s1 = "الفصل الأول: في بيان حقيقة الإيمان وأركانه";
        String s2 = "الفصل الأول: في بيان حقيقة الإيمان";
        assertTrue(ArabicTextNormalizer.similarity(s1, s2) >= 0.80);
    }
}
