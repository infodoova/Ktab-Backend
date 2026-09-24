package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicTextChecksTest {

    private static PagePlan page(String text) {
        return new PagePlan(1, text, "scene", List.of(), TextZone.TOP);
    }

    @Test
    void aGoodMsaPagePasses() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("ذَهَبَ سامي إِلَى المَدْرَسَةِ. كانَ سَعِيدًا."))).isEmpty();
    }

    @Test
    void tooManyWordsFails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10); // age 6-8: max 45
        String long46 = "كلمة ".repeat(46).trim() + ".";
        assertThat(DeterministicTextChecks.check(r, page(long46))).anyMatch(p -> p.contains("46 words"));
    }

    @Test
    void moreThanFourSentencesFails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("أ. ب. ج. د. هـ.")))
                .anyMatch(p -> p.contains("5 sentences"));
    }

    @Test
    void latinLettersFail() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("لعب سامي football.")))
                .anyMatch(p -> p.contains("Latin"));
    }

    @Test
    void tashkeelInADialectFails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.EGYPTIAN, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("سامي راحَ المدرسة.")))
                .anyMatch(p -> p.contains("tashkeel"));
    }

    @Test
    void dialectWithoutTashkeelPasses() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.EGYPTIAN, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("سامي راح المدرسة.")))
                .isEmpty();
    }

    @Test
    void emptyTextFails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        assertThat(DeterministicTextChecks.check(r, page("  "))).isNotEmpty();
    }
}
