package com.doova.ktab.features.storybook.story;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outfit lives in one place (the parent's choice, carried by the character sheet and the identity lock). A scene description
 * that names clothes or colours contradicts it, so the picture model follows the scene on some pages and the sheet on others.
 */
class SceneTextTest {

    @Test
    void theOutfitClauseGoesAndTheRestOfTheSceneStays() {
        String scene = "On a sunlit rooftop, CHILD studies an old star map while COMPANION perches beside her. "
                + "CHILD wears her coral-pink tunic, teal skirt, and fully covering lavender hijab; the calm, empty lower third is a rooftop floor for text.";

        String clean = SceneText.withoutOutfit(scene);

        assertThat(clean).doesNotContain("coral").doesNotContain("teal").doesNotContain("lavender").doesNotContain("wears");
        assertThat(clean).contains("CHILD studies an old star map").contains("the calm, empty lower third is a rooftop floor for text");
    }

    @Test
    void theFixedOutfitPhraseGoesToo() {
        String scene = "CHILD opens the old book at the library table. CHILD wears her fixed outfit and fully covering lavender hijab; the calm, empty lower third is a bare tabletop for text.";

        String clean = SceneText.withoutOutfit(scene);

        assertThat(clean).doesNotContain("lavender").doesNotContain("fixed outfit").doesNotContain("hijab");
        assertThat(clean).contains("CHILD opens the old book").contains("bare tabletop");
    }

    @Test
    void aCompanionsColoursAreRemovedButItsActionsStay() {
        String scene = "CHILD waves. CHILD wears a red coat, and COMPANION has emerald feathers and a turquoise right-leg band; COMPANION flies ahead.";

        String clean = SceneText.withoutOutfit(scene);

        assertThat(clean).doesNotContain("red coat").doesNotContain("emerald").doesNotContain("turquoise");
        assertThat(clean).contains("CHILD waves").contains("COMPANION flies ahead");
    }

    @Test
    void aWearingPhraseInsideASentenceIsRemoved() {
        String scene = "CHILD, wearing a bright red raincoat, jumps over a puddle in the lane.";

        String clean = SceneText.withoutOutfit(scene);

        assertThat(clean).doesNotContain("raincoat").doesNotContain("wearing");
        assertThat(clean).contains("CHILD").contains("jumps over a puddle in the lane");
    }

    @Test
    void anExpressionIsNotAnOutfit() {
        String scene = "CHILD wears a big smile as she opens the door. COMPANION wears a curious look.";

        assertThat(SceneText.withoutOutfit(scene)).isEqualTo(scene);
    }

    @Test
    void aSceneWithNoClothesIsLeftExactlyAsItWas() {
        String scene = "In a warm old Amman library, CHILD arranges books while COMPANION sits beside the window.";

        assertThat(SceneText.withoutOutfit(scene)).isEqualTo(scene);
        assertThat(SceneText.withoutOutfit(null)).isNull();
        assertThat(SceneText.withoutOutfit("  ")).isEqualTo("  ");
    }

    @Test
    void ifNothingUsefulWouldBeLeftTheOriginalIsKept() {
        String scene = "CHILD wears a green dress.";

        assertThat(SceneText.withoutOutfit(scene)).isEqualTo(scene);
    }

    @Test
    void aSupportingCharactersOutfitIsRemovedLikeAnyOther() {
        String scene = "CHILD shows the map to SUPPORT_1. SUPPORT_1 wears a grey coat and a red scarf; the calm lower third is a stone floor for text.";

        String clean = SceneText.withoutOutfit(scene);

        assertThat(clean).doesNotContain("grey coat").doesNotContain("red scarf").doesNotContain("wears");
        assertThat(clean).contains("CHILD shows the map to SUPPORT_1").contains("stone floor for text");
    }

    @Test
    void aSupportingClauseSharingASentenceWithTheCompanionIsSplitCleanly() {
        String scene = "CHILD waves. SUPPORT_1 wears a brown robe, and COMPANION flies ahead; the lower third is a wall.";

        String clean = SceneText.withoutOutfit(scene);

        assertThat(clean).doesNotContain("brown robe");
        assertThat(clean).contains("CHILD waves").contains("COMPANION flies ahead");
    }
}
