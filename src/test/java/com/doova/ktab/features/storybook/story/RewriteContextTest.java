package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A flagged page is rewritten alone, so the rewriter must be told who is in the story and what happens around the page. */
class RewriteContextTest {

    private static final String BLUEPRINT = "{\"titleConcept\":\"t\",\"premise\":\"p\",\"beats\":["
            + "{\"pageNumber\":1,\"beat\":\"ليلى في مكتبة الجد\",\"characters\":[\"ليلى\",\"الجد\"]},"
            + "{\"pageNumber\":2,\"beat\":\"تجد الخريطة\",\"characters\":[\"ليلى\",\"زمرد\"]},"
            + "{\"pageNumber\":3,\"beat\":\"تتبع الدليل\",\"characters\":[\"ليلى\",\"الجد\",\"زمرد\"]}]}";

    private static StoryPlanResponse plan() {
        return StoryFixtures.plan(3, "نص");
    }

    @Test
    void carriesTheBeatTheNeighboursAndTheWholeCast() {
        StoryPlanResponse plan = plan();

        RewriteContext c = RewriteContext.of(BLUEPRINT, plan, 2);

        assertThat(c.beat()).isEqualTo("تجد الخريطة");
        assertThat(c.previousText()).isEqualTo(plan.pages().get(0).textAr());
        assertThat(c.nextText()).isEqualTo(plan.pages().get(2).textAr());
        assertThat(c.cast()).containsExactlyInAnyOrder("ليلى", "الجد", "زمرد");
    }

    @Test
    void firstAndLastPagesHaveOnlyOneNeighbour() {
        assertThat(RewriteContext.of(BLUEPRINT, plan(), 1).previousText()).isNull();
        assertThat(RewriteContext.of(BLUEPRINT, plan(), 3).nextText()).isNull();
    }

    @Test
    void anUnreadableOrMissingBlueprintStillGivesTheNeighbours() {
        RewriteContext c = RewriteContext.of("not json", plan(), 2);

        assertThat(c.beat()).isNull();
        assertThat(c.cast()).isEmpty();
        assertThat(c.previousText()).isNotNull();
        assertThat(RewriteContext.of(null, plan(), 2).beat()).isNull();
    }

    @Test
    void theRewriteRequestShowsTheContextAndForbidsNewPeople() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        PagePlan page = plan().pages().get(1);
        RewriteContext c = new RewriteContext("تجد الخريطة", "الصفحة السابقة", "الصفحة التالية", List.of("ليلى", "الجد", "زمرد"));

        String msg = StoryPromptBuilder.rewriteUserMessage(r, "", page, List.of("problem"), c);

        assertThat(msg).contains("تجد الخريطة").contains("الصفحة السابقة").contains("الصفحة التالية")
                .contains("ليلى").contains("الجد").contains("زمرد")
                .containsIgnoringCase("do not add any person");
    }

    @Test
    void theRewriteRequestWithoutContextIsStillValid() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);

        String msg = StoryPromptBuilder.rewriteUserMessage(r, "", plan().pages().get(1), List.of("problem"), null);

        assertThat(msg).contains("problem");
    }

    @Test
    void storyPagesAreAskedToFillMostOfTheWordBudget() {
        StoryRequest r = StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10);
        int max = r.ageBand().maxWordsPerPage();

        String msg = StoryPromptBuilder.planUserMessage(r, "");

        assertThat(msg).contains("at most " + max + " words").contains("aim for " + (max * 6 / 10) + " to " + max);
    }
}
