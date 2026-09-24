package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterPromptsTest {

    @Test
    void sheetDescribesTheChildAndBothViews() {
        String p = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE);
        assertThat(p).contains("girl").contains("olive skin").contains("black short curly hair")
                .contains("front view").contains("three-quarter view").contains("No text");
    }

    @Test
    void hijabReplacesHairInTheSheet() {
        ChildAppearance withHijab = new ChildAppearance(ChildAppearance.SkinTone.LIGHT, null, null,
                ChildAppearance.EyeColor.GREEN, true, true);
        String p = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_9_10, withHijab);
        assertThat(p).contains("hijab").contains("glasses").doesNotContain("null");
    }

    @Test
    void sceneReservesTheTextZoneAndForbidsText() {
        String p = CharacterPrompts.scene("The CHILD kicks a ball in a park.", TextZone.BOTTOM, false, false);
        assertThat(p).contains("bottom third").contains("kicks a ball").contains("no text");
        assertThat(p).doesNotContain("COMPANION");
    }

    @Test
    void sceneMentionsTheCompanionReferenceWhenPresent() {
        assertThat(CharacterPrompts.scene("The CHILD and COMPANION run.", TextZone.TOP, true, false))
                .contains("companion sheet");
    }

    @Test
    void hijabIsRestatedInEveryScene() {
        assertThat(CharacterPrompts.scene("The CHILD reads.", TextZone.TOP, false, true)).contains("hijab");
    }
}
