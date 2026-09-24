package com.doova.ktab.features.storybook.render;

import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.enums.TextZone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RenderModelFactoryTest {

    private static List<RenderModelFactory.PageSource> sources() {
        return List.of(
                new RenderModelFactory.PageSource(2, PageKind.STORY, "لَعِبَ سامي.", TextZone.BOTTOM),
                new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ سامي.", TextZone.TOP));
    }

    @Test
    void ordersCoverDedicationStoryBack() {
        BookRenderModel m = RenderModelFactory.build("يومي", "سامي", null, TashkeelLevel.FULL, sources());

        assertThat(m.pages()).extracting(RenderPage::kind).containsExactly(
                RenderPage.Kind.COVER, RenderPage.Kind.DEDICATION, RenderPage.Kind.STORY, RenderPage.Kind.STORY, RenderPage.Kind.BACK);
        assertThat(m.pages().get(2).textAr()).isEqualTo("ذَهَبَ سامي.");
        assertThat(m.pages().get(2).imageFile()).isEqualTo("img/p1.jpg");
        assertThat(m.pages().get(0).imageFile()).isEqualTo("img/p0.jpg");
        assertThat(m.pages().get(1).imageFile()).isNull();
    }

    @Test
    void appliesTheTashkeelLevelEverywhere() {
        BookRenderModel m = RenderModelFactory.build("يَوْمِي", "سامي", null, TashkeelLevel.NONE, sources());
        assertThat(m.titleAr()).isEqualTo("يومي");
        assertThat(m.pages().get(2).textAr()).isEqualTo("ذهب سامي.");
        assertThat(m.pages().get(4).textAr()).doesNotContain("ُ");
    }

    @Test
    void parentDedicationWinsOverTheDefault() {
        assertThat(RenderModelFactory.build("t", "سامي", "إلى بطلنا", TashkeelLevel.FULL, sources()).pages().get(1).textAr())
                .isEqualTo("إلى بطلنا");
        assertThat(RenderModelFactory.build("t", "سامي", null, TashkeelLevel.FULL, sources()).pages().get(1).textAr())
                .isEqualTo("إلى سامي، بكل الحب.");
    }
}
