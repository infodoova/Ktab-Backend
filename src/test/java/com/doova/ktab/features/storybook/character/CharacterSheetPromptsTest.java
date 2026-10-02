package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The sheet is what every page is drawn from, so it must carry the locked outfit, a clean backdrop and one art style. */
class CharacterSheetPromptsTest {

    private static final CompanionSpec PARROT = new CompanionSpec(CompanionSpec.CompanionType.PARROT, "زمرد", null,
            CompanionSpec.PetColor.GREEN);

    @Test
    void theLockedOutfitIsPartOfTheSheetPrompt() {
        String prompt = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_9_10, StoryFixtures.APPEARANCE,
                "a soft turquoise hijab, a coral tunic and navy trousers");

        assertThat(prompt).contains("a soft turquoise hijab, a coral tunic and navy trousers")
                .containsIgnoringCase("do not change colours");
    }

    @Test
    void withoutAnOutfitThePromptIsTheOneItAlwaysWas() {
        String withNone = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_9_10, StoryFixtures.APPEARANCE, null);
        String blank = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_9_10, StoryFixtures.APPEARANCE, "  ");

        assertThat(withNone).isEqualTo(CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_9_10, StoryFixtures.APPEARANCE));
        assertThat(blank).isEqualTo(withNone);
        assertThat(withNone).doesNotContain("Outfit,");
    }

    @Test
    void everySheetAsksForAPureFlatWhiteBackdrop() {
        String backdrop = "pure flat white";

        assertThat(CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_9_10, StoryFixtures.APPEARANCE)).contains(backdrop);
        assertThat(CharacterPrompts.sheetFromPhoto(ChildGender.GIRL, AgeBand.AGE_9_10)).contains(backdrop);
        assertThat(CharacterPrompts.sheetFromPreviousSheet(ChildGender.GIRL, AgeBand.AGE_9_10)).contains(backdrop);
        assertThat(CharacterPrompts.companionSheet(PARROT)).contains(backdrop);
    }

    @Test
    void theCompanionIsToMatchTheChildSheetsDrawingStyle() {
        String prompt = CharacterPrompts.companionSheet(PARROT, true);

        assertThat(prompt).contains("second reference image is the child's character sheet")
                .contains("same art style, line work");
        assertThat(CharacterPrompts.companionSheet(PARROT, false)).doesNotContain("second reference image");
        assertThat(CharacterPrompts.companionSheet(PARROT)).isEqualTo(CharacterPrompts.companionSheet(PARROT, false));
    }

    @Test
    void aSupportingCharactersSheetCarriesItsDescriptionOutfitStyleAndBackdrop() {
        String prompt = CharacterPrompts.supportingSheet("the grandfather, a man, about 68 years old", "a brown jalabiya and a white keffiyeh", true);

        assertThat(prompt).contains("the grandfather, a man, about 68 years old")
                .contains("a brown jalabiya and a white keffiyeh").containsIgnoringCase("do not change colours")
                .contains("front view on the left").contains("pure flat white")
                .contains("second reference image is the child's character sheet").contains("same art style, line work");
    }

    @Test
    void aSupportingCharacterWithoutAnOutfitGetsModestClothesAndNoOutfitSentence() {
        String prompt = CharacterPrompts.supportingSheet("the friend, a girl, about 9 years old", null, false);

        assertThat(prompt).doesNotContain("Outfit,").containsIgnoringCase("modest").doesNotContain("second reference image");
    }
}
