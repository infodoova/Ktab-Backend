package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Characters beyond the child and the companion are told apart by a tag (SUPPORT_1, SUPPORT_2) so every page names them the same way. */
class SupportingCastTest {

    private static StorybookCharacter grandfather() {
        StorybookCharacter c = new StorybookCharacter();
        c.setKind(CharacterKind.SUPPORTING);
        c.setCharacterId("grandpa");
        c.setRole("Elder");
        c.setRelationship("grandfather");
        c.setClothing("a brown jalabiya and a white keffiyeh");
        c.setPersonality(List.of("wise", "calm"));
        c.setAdvancedDetails(Map.of("name", "الجد", "age", 68, "gender", "BOY", "signatureItem", "a brass pocket watch"));
        return c;
    }

    @Test
    void theTagFollowsTheOrderOfTheCast() {
        StorybookCharacter friend = new StorybookCharacter();
        friend.setKind(CharacterKind.SUPPORTING);
        friend.setCharacterId("salma");
        friend.setRelationship("friend");

        List<SupportingCast> cast = SupportingCast.of(List.of(grandfather(), friend));

        assertThat(cast).extracting(SupportingCast::ref).containsExactly("SUPPORT_1", "SUPPORT_2");
        assertThat(cast.get(0).name()).isEqualTo("الجد");
        assertThat(cast.get(1).name()).isEqualTo("salma"); // no name given: the id stands in
    }

    @Test
    void theEnglishDescriptionCarriesWhatTheSheetNeeds() {
        SupportingCast g = SupportingCast.of(List.of(grandfather())).get(0);

        assertThat(g.describeEn()).contains("grandfather").contains("68").contains("man")
                .contains("wise, calm").contains("a brass pocket watch");
        assertThat(g.clothing()).isEqualTo("a brown jalabiya and a white keffiyeh");
    }

    @Test
    void aChildAgedCharacterIsABoyOrGirlNotAManOrWoman() {
        StorybookCharacter kid = new StorybookCharacter();
        kid.setKind(CharacterKind.SUPPORTING);
        kid.setCharacterId("omar");
        kid.setRelationship("cousin");
        kid.setAdvancedDetails(Map.of("age", 9, "gender", "BOY"));

        assertThat(SupportingCast.of(List.of(kid)).get(0).describeEn()).contains("boy").doesNotContain(" man");
    }

    @Test
    void theLegendTellsTheWriterWhoEachTagIsAndForbidsOtherPeople() {
        String legend = SupportingCast.legend(SupportingCast.of(List.of(grandfather())));

        assertThat(legend).contains("SUPPORT_1").contains("الجد").contains("grandfather")
                .containsIgnoringCase("only by its tag").containsIgnoringCase("do not invent other people");
    }

    @Test
    void noSupportingCharactersMeansNoLegend() {
        assertThat(SupportingCast.legend(List.of())).isEmpty();
        assertThat(SupportingCast.legend(null)).isEmpty();
        assertThat(SupportingCast.of(null)).isEmpty();
    }
}
