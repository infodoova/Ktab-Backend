package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.character.CharacterPrompts;
import com.doova.ktab.features.storybook.enums.TextZone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The style bible is written once per book and appended to every page prompt, so all pages share one art direction. */
class StyleBibleTest {

    @Test
    void notesSurviveARoundTripThroughTheJsonColumn() {
        String json = StyleBible.toJson("soft watercolor, \"warm\" light");

        assertThat(StyleBible.notesOf(json)).isEqualTo("soft watercolor, \"warm\" light");
    }

    @Test
    void emptyOrUnreadableInputGivesNoNotes() {
        assertThat(StyleBible.toJson(null)).isNull();
        assertThat(StyleBible.toJson("  ")).isNull();
        assertThat(StyleBible.notesOf(null)).isNull();
        assertThat(StyleBible.notesOf("not json")).isNull();
        assertThat(StyleBible.notesOf("{}")).isNull();
    }

    @Test
    void theScenePromptKeepsItsOldTextWhenThereIsNoLock() {
        String old = CharacterPrompts.scene("The CHILD waves.", TextZone.TOP, true, true);

        assertThat(old).doesNotContain("Identity lock").doesNotContain("Art direction");
    }

    @Test
    void theScenePromptCarriesTheLockAndTheArtDirection() {
        CharacterPrompts.PageLock lock = new CharacterPrompts.PageLock(true, false, "light olive skin", "a turquoise hijab and a coral tunic",
                "a small green pet parrot", "soft watercolor, warm light", true);

        String prompt = CharacterPrompts.scene("The CHILD waves.", TextZone.TOP, true, lock);

        assertThat(prompt).contains("Identity lock for the CHILD").contains("wears NO glasses").contains("light olive skin")
                .contains("a turquoise hijab and a coral tunic").contains("the same hijab")
                .contains("Identity lock for the COMPANION").contains("a small green pet parrot")
                .contains("Art direction for the whole book").contains("soft watercolor, warm light")
                .contains("last reference image is the approved book cover");
    }

    @Test
    void aChildWithGlassesIsToldToKeepThem() {
        CharacterPrompts.PageLock lock = new CharacterPrompts.PageLock(false, true, null, null, null, null, false);

        String prompt = CharacterPrompts.scene("The CHILD reads.", TextZone.BOTTOM, false, lock);

        assertThat(prompt).contains("always wears the same glasses").doesNotContain("NO glasses").doesNotContain("approved book cover");
    }

    @Test
    void aSupportingCharacterOnThePageGetsItsOwnLockAndAGuideToTheReferenceImages() {
        CharacterPrompts.PageLock lock = new CharacterPrompts.PageLock(true, false, "light olive skin", "a turquoise hijab", "a small green pet parrot",
                "soft watercolor", true,
                java.util.List.of(new CharacterPrompts.SupportingLook("SUPPORT_1", "the grandfather, a man, about 68 years old", "a brown jalabiya and a white keffiyeh")));

        String prompt = CharacterPrompts.scene("CHILD shows SUPPORT_1 the map.", TextZone.BOTTOM, true, lock);

        assertThat(prompt).contains("Identity lock for SUPPORT_1").contains("the grandfather, a man, about 68 years old")
                .contains("a brown jalabiya and a white keffiyeh").contains("exactly like its character sheet")
                .contains("Reference images, in order: CHILD sheet, style reference, COMPANION sheet, SUPPORT_1 sheet, approved book cover");
    }

    @Test
    void withoutSupportingCharactersThereIsNoGuideAndTheOldLockIsUnchanged() {
        CharacterPrompts.PageLock plain = new CharacterPrompts.PageLock(true, false, "light olive skin", "a turquoise hijab", null, null, false);

        String prompt = CharacterPrompts.scene("CHILD waves.", TextZone.TOP, false, plain);

        assertThat(prompt).doesNotContain("Reference images, in order").doesNotContain("SUPPORT_");
    }

    @Test
    void theGuideLeavesOutTheStyleReferenceWhenItWasDropped() {
        CharacterPrompts.PageLock lock = new CharacterPrompts.PageLock(true, false, null, null, null, null, true,
                java.util.List.of(new CharacterPrompts.SupportingLook("SUPPORT_1", "the grandfather", null)), false);

        String prompt = CharacterPrompts.scene("CHILD walks with SUPPORT_1.", TextZone.BOTTOM, true, lock);

        assertThat(prompt).contains("Reference images, in order: CHILD sheet, COMPANION sheet, SUPPORT_1 sheet, approved book cover")
                .doesNotContain("style reference,");
    }
}
