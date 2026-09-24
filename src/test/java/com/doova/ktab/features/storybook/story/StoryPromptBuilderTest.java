package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.character.CompanionSpec;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryPromptBuilderTest {

    @Test
    void msaRequestAsksForFullVocalizationAndCarriesTheDetails() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.GIRL, 10);

        String msg = StoryPromptBuilder.planUserMessage(r, "");

        assertThat(msg).contains("«سامي»")
                .contains("girl")
                .contains("at most 45 words")
                .contains("fully vocalized")
                .contains("playing football")
                .contains("Beirut");
        assertThat(msg).contains("Page 10:").doesNotContain("Page 11:");
    }

    @Test
    void dialectRequestForbidsTashkeelAndIncludesTheGuide() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.LEBANESE, ChildGender.BOY, 12);

        String msg = StoryPromptBuilder.planUserMessage(r, "# Lebanese dialect style guide\n1. No tashkeel.");

        assertThat(msg).contains("Lebanese Arabic").contains("no tashkeel").contains("# Lebanese dialect style guide");
        assertThat(msg).doesNotContain("fully vocalized");
    }

    @Test
    void companionIsDescribedWhenPresent() {
        StoryRequest base = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        StoryRequest r = new StoryRequest(base.childNameAr(), base.gender(), base.ageBand(), base.appearance(),
                base.variety(), base.blueprint(), base.pageCount(), base.interests(),
                new CompanionSpec(CompanionSpec.CompanionType.CAT, "بسبوسة", null, CompanionSpec.PetColor.ORANGE),
                base.setting());

        assertThat(StoryPromptBuilder.planUserMessage(r, "")).contains("«بسبوسة»").contains("orange cat");
    }

    @Test
    void rewriteMessageListsTheProblems() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.GIRL, 10);
        PagePlan page = StoryFixtures.plan(10, "ذهبَ سامي").pages().get(2);

        String msg = StoryPromptBuilder.rewriteUserMessage(r, "", page, List.of("verb ذهبَ should be feminine"));

        assertThat(msg).contains("Page 3").contains("verb ذهبَ should be feminine").contains("ذهبَ سامي");
    }
}
