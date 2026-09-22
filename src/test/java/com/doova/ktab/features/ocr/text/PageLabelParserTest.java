package com.doova.ktab.features.ocr.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PageLabelParserTest {

    @Test
    @DisplayName("parseNumeric should parse ASCII digits correctly")
    void parseNumeric_asciiDigits_returnsNumber() {
        Optional<Integer> result = PageLabelParser.parseNumeric("45");
        assertTrue(result.isPresent());
        assertEquals(45, result.get());
    }

    @Test
    @DisplayName("parseNumeric should parse Arabic-Indic digits")
    void parseNumeric_arabicIndicDigits_returnsNumber() {
        Optional<Integer> result = PageLabelParser.parseNumeric("٤٥");
        assertTrue(result.isPresent());
        assertEquals(45, result.get());
    }

    @Test
    @DisplayName("parseNumeric should parse Persian/Extended digits")
    void parseNumeric_persianDigits_returnsNumber() {
        Optional<Integer> result = PageLabelParser.parseNumeric("۴۵");
        assertTrue(result.isPresent());
        assertEquals(45, result.get());
    }

    @Test
    @DisplayName("parseNumeric should handle digits enclosed in dashes or brackets")
    void parseNumeric_decoratedDigits_returnsNumber() {
        Optional<Integer> result = PageLabelParser.parseNumeric("- 123 -");
        assertTrue(result.isPresent());
        assertEquals(123, result.get());
    }

    @Test
    @DisplayName("isFrontMatter should recognize Arabic abjad letters")
    void isFrontMatter_abjadLetters_returnsTrue() {
        assertTrue(PageLabelParser.isFrontMatter("ج"));
        assertTrue(PageLabelParser.isFrontMatter("أ"));
        assertTrue(PageLabelParser.isFrontMatter("هـ"));
    }

    @Test
    @DisplayName("isFrontMatter should recognize Roman numerals")
    void isFrontMatter_romanNumerals_returnsTrue() {
        assertTrue(PageLabelParser.isFrontMatter("xii"));
        assertTrue(PageLabelParser.isFrontMatter("iv"));
        assertTrue(PageLabelParser.isFrontMatter("VIII"));
    }

    @Test
    @DisplayName("parseNumeric should return empty for non-numeric labels")
    void parseNumeric_nonNumeric_returnsEmpty() {
        assertTrue(PageLabelParser.parseNumeric("ج").isEmpty());
        assertTrue(PageLabelParser.parseNumeric("iv").isEmpty());
        assertTrue(PageLabelParser.parseNumeric("").isEmpty());
        assertTrue(PageLabelParser.parseNumeric(null).isEmpty());
    }
}
