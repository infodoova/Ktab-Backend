package com.doova.ktab.features.ocr.harmonize;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HarmonizationGuardTest {

    private final HarmonizationGuard guard = new HarmonizationGuard();

    @Test
    @DisplayName("isValid should accept clean text with minor formatting fixes")
    void isValid_minorFormattingClean_returnsTrue() {
        String raw = "هذا نص تجريبي يحتوي على سطر مكسور \n في المنتصف مع نقطة.";
        String clean = "هذا نص تجريبي يحتوي على سطر مكسور في المنتصف مع نقطة.";
        assertTrue(guard.isValid(raw, clean));
    }

    @Test
    @DisplayName("isValid should reject unauthorized rewriting of words")
    void isValid_rewrittenWords_returnsFalse() {
        String raw = "ذهب الطالب إلى المدرسة في الصباح الباكر لطلب العلم والمعرفة.";
        String clean = "انطلق التلميذ نحو المؤسسة التعليمية فجراً لاكتساب العلوم.";
        assertFalse(guard.isValid(raw, clean));
    }

    @Test
    @DisplayName("isValid should reject output when length changes by more than 8%")
    void isValid_lengthDeltaExceedsThreshold_returnsFalse() {
        String raw = "هذا نص أصلي قصير جداً لا يتجاوز بضع كلمات.";
        String clean = "هذا نص أصلي قصير جداً لا يتجاوز بضع كلمات مع إضافة فقرة طويلة كاملة غير موجودة بالأصل تزيد الطول كثيراً.";
        assertFalse(guard.isValid(raw, clean));
    }
}
