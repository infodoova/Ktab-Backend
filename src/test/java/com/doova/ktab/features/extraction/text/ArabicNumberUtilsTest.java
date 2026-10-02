package com.doova.ktab.features.extraction.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArabicNumberUtilsTest {

    @Test
    void convertsAllThreeDigitFamilies() {
        assertThat(ArabicNumberUtils.toAsciiDigits("١٢٣ ۱۲۳ 123")).isEqualTo("123 123 123");
    }

    @Test
    void parsesAStandalonePageNumber() {
        assertThat(ArabicNumberUtils.parsePageNumber(" ٤٢ ")).hasValue(42);
        assertThat(ArabicNumberUtils.parsePageNumber("- ٧ -")).hasValue(7);
        assertThat(ArabicNumberUtils.parsePageNumber("۱۷")).hasValue(17);
        assertThat(ArabicNumberUtils.parsePageNumber("(12)")).hasValue(12);
    }

    @Test
    void doesNotTreatTextWithANumberAsAPageNumber() {
        assertThat(ArabicNumberUtils.parsePageNumber("الفصل ٢")).isEmpty();
        assertThat(ArabicNumberUtils.parsePageNumber("")).isEmpty();
        assertThat(ArabicNumberUtils.parsePageNumber(null)).isEmpty();
        assertThat(ArabicNumberUtils.parsePageNumber("12345")).isEmpty(); // more than 4 digits is not a page number
    }
}
