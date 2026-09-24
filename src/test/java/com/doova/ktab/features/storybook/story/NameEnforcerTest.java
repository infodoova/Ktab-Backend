package com.doova.ktab.features.storybook.story;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameEnforcerTest {

    @Test
    void replacesAVocalizedNameWithTheTypedOne() {
        assertThat(NameEnforcer.enforce("ذَهَبَ مُحَمَّدٌ إِلَى البَيْتِ.", "محمد"))
                .isEqualTo("ذَهَبَ محمد إِلَى البَيْتِ.");
    }

    @Test
    void keepsTheParentsTashkeelWhenTheyTypedIt() {
        assertThat(NameEnforcer.enforce("قال محمد: «مرحبا!»", "مُحَمَّد"))
                .isEqualTo("قال مُحَمَّد: «مرحبا!»");
    }

    @Test
    void fixesAlifVariants() {
        assertThat(NameEnforcer.enforce("لعب احمد مع أحمدَ", "أحمد")).isEqualTo("لعب أحمد مع أحمد");
    }

    @Test
    void fixesTaMarbutaVariant() {
        assertThat(NameEnforcer.enforce("ضحكت فاطمه", "فاطمة")).isEqualTo("ضحكت فاطمة");
    }

    @Test
    void keepsAProcliticAttached() {
        assertThat(NameEnforcer.enforce("ولِمُحَمَّدٍ صديقٌ", "محمد")).isEqualTo("ولمحمد صديقٌ");
    }

    @Test
    void doesNotTouchLongerWordsThatContainTheName() {
        assertThat(NameEnforcer.enforce("المحمدية مدينة", "محمد")).isEqualTo("المحمدية مدينة");
    }
}
