package com.doova.ktab.features.extraction.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArabicArtifactRepairTest {

    @Test
    void repairArtifacts_strayReplacementBesideDiacritic_isRemoved() {
        assertThat(ArabicTextCleaner.repairArtifacts("ت\uFFFDُدفن")).isEqualTo("تُدفن");
    }

    @Test
    void repairArtifacts_kashida_isStripped() {
        assertThat(ArabicTextCleaner.repairArtifacts("كـانـت فـكـرة")).isEqualTo("كانت فكرة");
    }

    @Test
    void repairArtifacts_replacementOutsideArabicWord_isKept() {
        assertThat(ArabicTextCleaner.repairArtifacts("abc \uFFFD def")).isEqualTo("abc \uFFFD def");
    }

    @Test
    void repairArtifacts_null_returnsNull() {
        assertThat(ArabicTextCleaner.repairArtifacts(null)).isNull();
    }
}
