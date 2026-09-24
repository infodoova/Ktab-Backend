package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TashkeelFilterTest {

    private static final String VOCALIZED = "ذَهَبَ مُحَمَّدٌ إِلَى المَدْرَسَةِ";

    @Test
    void fullKeepsEverything() {
        assertThat(TashkeelFilter.apply(VOCALIZED, TashkeelLevel.FULL)).isEqualTo(VOCALIZED);
    }

    @Test
    void noneStripsEveryMark() {
        assertThat(TashkeelFilter.apply(VOCALIZED, TashkeelLevel.NONE)).isEqualTo("ذهب محمد إلى المدرسة");
    }

    @Test
    void partialKeepsOnlyShaddaAndTanween() {
        // مُحَمَّدٌ keeps shadda (ّ) and dammatan (ٌ); every fatha/damma/kasra/sukun goes.
        assertThat(TashkeelFilter.apply(VOCALIZED, TashkeelLevel.PARTIAL)).isEqualTo("ذهب محمّدٌ إلى المدرسة");
    }

    @Test
    void lettersWithHamzaAreUntouched() {
        assertThat(TashkeelFilter.apply("أُمّي", TashkeelLevel.NONE)).isEqualTo("أمي");
    }
}
