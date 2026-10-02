package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.llm.LlmCall;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoryWriterTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final StoryWriter writer = new StoryWriter(llm, new PromptLibrary());

    @Test
    void returnsThePlanAndUsesTheStaticSystemPrompt() {
        llm.enqueue(StoryFixtures.plan(10, "نَصٌّ"));

        LlmCall<StoryPlanResponse> call = writer.writePlan(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10));

        assertThat(call.value().pages()).hasSize(10);
        assertThat(llm.requests()).singleElement().satisfies(r -> {
            assertThat(r.purpose()).isEqualTo(LlmPurpose.STORY_PLAN);
            assertThat(r.system()).startsWith("You write personalized Arabic picture books");
            assertThat(r.responseType()).isEqualTo(StoryPlanResponse.class);
        });
    }

    @Test
    void rejectsWrongPageCount() {
        llm.enqueue(StoryFixtures.plan(9, "نَصٌّ"));

        assertThatThrownBy(() -> writer.writePlan(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10)))
                .isInstanceOf(StoryPlanInvalidException.class)
                .hasMessageContaining("expected 10");
    }

    @Test
    void rejectsPagesNotNumberedOneToN() {
        StoryPlanResponse zeroBased = new StoryPlanResponse("t", "c", StoryFixtures.plan(10, "نَصٌّ").pages().stream()
                .map(p -> new PagePlan(p.pageNumber() - 1, p.textAr(), p.sceneEn(), p.characters(), p.textZone()))
                .toList());
        llm.enqueue(zeroBased);

        assertThatThrownBy(() -> writer.writePlan(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10)))
                .isInstanceOf(StoryPlanInvalidException.class);
    }

    @Test
    void rewriteKeepsThePageNumberEvenIfTheModelChangesIt() {
        PagePlan original = StoryFixtures.plan(10, "ذهبَ").pages().get(4);
        llm.enqueue(new PagePlan(99, "ذهبتْ", original.sceneEn(), original.characters(), original.textZone()));

        LlmCall<PagePlan> call = writer.rewritePage(StoryFixtures.request(LanguageVariety.MSA, ChildGender.GIRL, 10),
                original, List.of("gender"));

        assertThat(call.value().pageNumber()).isEqualTo(5);
        assertThat(call.value().textAr()).isEqualTo("ذهبتْ");
        assertThat(llm.requests().get(0).purpose()).isEqualTo(LlmPurpose.STORY_PAGE_REWRITE);
    }

    @Test
    void rewriteTitleReturnsTheNewTitleAndAsksOnlyForTheTitle() {
        llm.enqueue(new TitleRewriteResponse("خَرِيطَةُ النُّجُومِ الْمَنْسِيَّةُ"));

        LlmCall<TitleRewriteResponse> call = writer.rewriteTitle(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                "عنوان قديم", List.of("case ending"), new RewriteContext("beat", null, null, List.of("ليلى")));

        assertThat(call.value().titleAr()).isEqualTo("خَرِيطَةُ النُّجُومِ الْمَنْسِيَّةُ");
        assertThat(llm.requests().get(0).user()).contains("عنوان قديم").contains("case ending");
    }

    @Test
    void anEmptyTitleRewriteIsUnusable() {
        assertThat(new TitleRewriteResponse(" ").problems()).isNotEmpty();
        assertThat(new TitleRewriteResponse("عنوان").problems()).isEmpty();
    }

    @Test
    void aPlanedSceneNeverKeepsAnOutfit() {
        PagePlan page = new PagePlan(1, "نص", "CHILD reads. CHILD wears her lavender hijab; the calm, empty lower third is a rug for text.",
                List.of(), com.doova.ktab.features.storybook.enums.TextZone.BOTTOM);

        PagePlan normalized = page.normalized(1);

        assertThat(normalized.sceneEn()).doesNotContain("lavender").contains("CHILD reads").contains("rug for text");
    }

    @Test
    void aCoverSceneNeverKeepsAnOutfitEither() {
        StoryPlanResponse plan = new StoryPlanResponse("t", "CHILD stands on a rooftop. CHILD wears a coral-pink tunic and a lavender hijab; the calm top third is sky.", List.of());

        assertThat(plan.normalized("d", "c").coverSceneEn()).doesNotContain("coral").doesNotContain("lavender").contains("rooftop");
    }
}
