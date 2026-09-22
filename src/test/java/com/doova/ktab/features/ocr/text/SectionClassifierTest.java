package com.doova.ktab.features.ocr.text;

import com.doova.ktab.enums.book.SectionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SectionClassifierTest {

    @Test
    @DisplayName("classify should identify standard Arabic section types correctly")
    void classify_standardArabicKeywords_correctSectionType() {
        assertEquals(SectionType.DEDICATION, SectionClassifier.classify("إهداء إلى روح والدي"));
        assertEquals(SectionType.FOREWORD, SectionClassifier.classify("كلمة تقديم"));
        assertEquals(SectionType.INTRODUCTION, SectionClassifier.classify("المقدمة العامة للكتاب"));
        assertEquals(SectionType.PART, SectionClassifier.classify("الباب الأول: في قواعد التفسير"));
        assertEquals(SectionType.CHAPTER, SectionClassifier.classify("الفصل الثاني: منهج التحقيق"));
        assertEquals(SectionType.SUBSECTION, SectionClassifier.classify("المبحث الثالث: الروايات"));
        assertEquals(SectionType.CONCLUSION, SectionClassifier.classify("الخاتمة والتوصيات"));
        assertEquals(SectionType.APPENDIX, SectionClassifier.classify("الملاحق والوثائق"));
        assertEquals(SectionType.BIBLIOGRAPHY, SectionClassifier.classify("ثبت المصادر والمراجع"));
        assertEquals(SectionType.INDEX, SectionClassifier.classify("فهرس الأعلام والشخصيات"));
        assertEquals(SectionType.INDEX, SectionClassifier.classify("فهرس الآيات القرآنية"));
        assertEquals(SectionType.FRONT_MATTER, SectionClassifier.classify("فهرس الموضوعات"));
    }

    @Test
    @DisplayName("extractDivisionLabel should detect division words")
    void extractDivisionLabel_variousHeadings_extractsDivision() {
        assertEquals(Optional.of("الباب"), SectionClassifier.extractDivisionLabel("الباب الأول"));
        assertEquals(Optional.of("الفصل"), SectionClassifier.extractDivisionLabel("الفصل الثالث"));
        assertEquals(Optional.of("المبحث"), SectionClassifier.extractDivisionLabel("المبحث الثاني"));
        assertEquals(Optional.empty(), SectionClassifier.extractDivisionLabel("مقدمة الطبعة الثانية"));
    }

    @Test
    @DisplayName("extractOrdinal should parse Arabic ordinal words and numbers")
    void extractOrdinal_ordinalWordsAndNumbers_parsedCorrectly() {
        assertEquals(Optional.of(1), SectionClassifier.extractOrdinal("الفصل الأول"));
        assertEquals(Optional.of(2), SectionClassifier.extractOrdinal("الباب الثاني"));
        assertEquals(Optional.of(3), SectionClassifier.extractOrdinal("الفصل الثالث"));
        assertEquals(Optional.of(4), SectionClassifier.extractOrdinal("المبحث 4"));
        assertEquals(Optional.of(5), SectionClassifier.extractOrdinal("القسم الخامس"));
    }
}
