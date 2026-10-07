package com.doova.ktab.features.storybook.character;

import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Character Prompts Comprehensive Specification Tests")
class CharacterPromptsComprehensiveTest {

    @Test
    void sheet_boyAgedSixToEight_generatesThreeViewsWithModestLongSleevesAndArtStyle() {
        String prompt = CharacterPrompts.sheet(ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE);

        assertThat(prompt)
                .contains("children's picture book in 3:4 portrait orientation")
                .contains("One boy aged about 7")
                .contains("shown three times side by side on a plain white background")
                .contains("front view on the left")
                .contains("three-quarter view in the center")
                .contains("side profile view on the right")
                .contains("Full body, standing, gentle smile, identical locked outfit in all three views")
                .contains("Appearance: olive skin, black short curly hair, brown eyes")
                .contains("Modest, simple, bright everyday children's clothes with long sleeves")
                .contains("Match the art style of the style reference image exactly")
                .contains("No text, labels or captions")
                .contains("Absolutely no text, letters, numbers, signs, logos or writing anywhere in the picture");
    }

    @Test
    void sheet_girlWithHijab_includesHijabLockAndExcludesHairSpecifications() {
        ChildAppearance appearanceWithHijab = new ChildAppearance(
                ChildAppearance.SkinTone.LIGHT,
                null, // hair color replaced by hijab
                null, // hair style replaced by hijab
                ChildAppearance.EyeColor.GREEN,
                true,  // hijab
                true   // glasses
        );

        String prompt = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_9_10, appearanceWithHijab);

        assertThat(prompt)
                .contains("One girl aged about 9")
                .contains("light skin")
                .contains("green eyes")
                .contains("wears a simple plain hijab that fully covers the hair")
                .contains("round glasses")
                .doesNotContain("curly")
                .doesNotContain("straight")
                .doesNotContain("null");
    }

    @Test
    void sheet_childWithGlassesAndFreckles_describesSpecificFacialFeatures() {
        ChildAppearance appearance = new ChildAppearance(
                ChildAppearance.SkinTone.LIGHT,
                ChildAppearance.HairColor.BROWN,
                ChildAppearance.HairStyle.LONG_STRAIGHT,
                ChildAppearance.EyeColor.HAZEL,
                false,
                true
        );

        String prompt = CharacterPrompts.sheet(ChildGender.GIRL, AgeBand.AGE_3_5, appearance);

        assertThat(prompt)
                .contains("One girl aged about 4")
                .contains("light skin")
                .contains("brown long straight hair")
                .contains("hazel eyes")
                .contains("round glasses")
                .doesNotContain("hijab");
    }

    @Test
    void sheet_allAgeBands_computesAccurateMidpointAges() {
        String p35 = CharacterPrompts.sheet(ChildGender.BOY, AgeBand.AGE_3_5, StoryFixtures.APPEARANCE);
        String p68 = CharacterPrompts.sheet(ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE);
        String p910 = CharacterPrompts.sheet(ChildGender.BOY, AgeBand.AGE_9_10, StoryFixtures.APPEARANCE);

        assertThat(p35).contains("aged about 4");
        assertThat(p68).contains("aged about 7");
        assertThat(p910).contains("aged about 9");
    }

    @Test
    void sheetFromPhoto_boyAgedNineToTen_preservesFacialFeaturesAndLocksThreeViews() {
        String prompt = CharacterPrompts.sheetFromPhoto(ChildGender.BOY, AgeBand.AGE_9_10);

        assertThat(prompt)
                .contains("stylized children's picture-book character of the child in the photo reference")
                .contains("keeping their recognizable features: face shape, skin tone, hair colour and style, eye colour, glasses and headwear")
                .contains("A boy aged about 9")
                .contains("front view on the left")
                .contains("three-quarter view in the center")
                .contains("side profile view on the right")
                .contains("identical locked outfit in all three views")
                .contains("Modest, simple, bright everyday children's clothes with long sleeves")
                .contains("Match the art style of the style reference image exactly");
    }

    @Test
    void sheetFromPreviousSheet_childReference_requestsFreshPoseWithIdenticalLockedFeatures() {
        String prompt = CharacterPrompts.sheetFromPreviousSheet(ChildGender.GIRL, AgeBand.AGE_6_8);

        assertThat(prompt)
                .contains("Create a new version of the character in the first reference image")
                .contains("the same child, keeping their face shape, skin tone, hair, eye colour, glasses and headwear")
                .contains("with a fresh pose and outfit")
                .contains("A girl aged about 7")
                .contains("front view on the left")
                .contains("three-quarter view in the center")
                .contains("side profile view on the right");
    }

    @Test
    void companionSheet_friendlyCompanion_generatesThreeViewsAndFriendlyExpression() {
        CompanionSpec companion = new CompanionSpec(CompanionSpec.CompanionType.CAT, "بسبوس", null, CompanionSpec.PetColor.ORANGE);
        String prompt = CharacterPrompts.companionSheet(companion);

        assertThat(prompt)
                .contains("Character reference master sheet for a children's picture book in 3:4 portrait orientation")
                .contains("a orange cat")
                .contains("front view on the left")
                .contains("three-quarter view in the center")
                .contains("side profile view on the right")
                .contains("Friendly expression")
                .contains("Match the art style of the style reference image exactly")
                .contains("Absolutely no text");
    }

    @Test
    void scene_bottomTextZone_containsBottomThirdAndBottom20PercentConstraint() {
        String prompt = CharacterPrompts.scene("Sami is playing with blocks on a soft carpet.", TextZone.BOTTOM, false, false);

        assertThat(prompt)
                .contains("Illustrate one page of a children's picture book in 1:1 square orientation")
                .contains("Match the art style of the style reference image exactly")
                .contains("The CHILD is exactly the character in the character sheet")
                .contains("Scene: Sami is playing with blocks on a soft carpet.")
                .contains("Composition: keep the bottom text-safe area, approximately 20% of the picture")
                .contains("plain sky, wall, grass, water or floor")
                .contains("Reserve it exclusively for later text placement")
                .doesNotContain("COMPANION")
                .doesNotContain("hijab");
    }

    @Test
    void scene_topTextZone_containsTopThirdConstraint() {
        String prompt = CharacterPrompts.scene("Sami looks at the stars.", TextZone.TOP, false, false);

        assertThat(prompt)
                .contains("Composition: keep the top text-safe area, approximately 20% of the picture")
                .contains("Reserve it exclusively for later text placement");
    }

    @Test
    void scene_withCompanionTrue_referencesCompanionMasterSheet() {
        String prompt = CharacterPrompts.scene("Sami feeds the cat.", TextZone.BOTTOM, true, false);

        assertThat(prompt)
                .contains("The COMPANION is exactly the character in the companion sheet.")
                .contains("The CHILD is exactly the character in the character sheet");
    }

    @Test
    void scene_withCompanionFalse_omitsCompanionMasterSheet() {
        String prompt = CharacterPrompts.scene("Sami reads alone.", TextZone.BOTTOM, false, false);

        assertThat(prompt).doesNotContain("COMPANION");
    }

    @Test
    void scene_withHijabTrue_restatesHijabRequirement() {
        String prompt = CharacterPrompts.scene("Fatima waves hello.", TextZone.BOTTOM, false, true);

        assertThat(prompt).contains("always wearing the same hijab");
    }

    @Test
    void scene_withHijabFalse_omitsHijabRequirement() {
        String prompt = CharacterPrompts.scene("Fatima waves hello.", TextZone.BOTTOM, false, false);

        assertThat(prompt).doesNotContain("hijab");
    }

    @Test
    void cover_withoutCompanion_placesChildInProminentPositionWithTopZone() {
        String prompt = CharacterPrompts.cover("Sami under a rainbow.", false, false);

        assertThat(prompt)
                .contains("This is the book cover: warm, inviting, visually memorable, with the CHILD clearly visible and immediately recognizable.")
                .contains("Composition: keep the top text-safe area")
                .doesNotContain("COMPANION")
                .doesNotContain("hijab");
    }

    @Test
    void cover_withCompanionAndHijab_combinesAllDirectives() {
        String prompt = CharacterPrompts.cover("Aya and her kitten exploring.", true, true);

        assertThat(prompt)
                .contains("This is the book cover: warm, inviting, visually memorable, with the CHILD clearly visible and immediately recognizable.")
                .contains("The COMPANION is exactly the character in the companion sheet.")
                .contains("always wearing the same hijab")
                .contains("Composition: keep the top text-safe area");
    }
}
