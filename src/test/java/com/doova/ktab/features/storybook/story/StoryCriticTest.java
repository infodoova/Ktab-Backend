package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StoryCriticTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final StoryCritic critic = new StoryCritic(llm, new PromptLibrary());

    private static CriticResponse allPass(int pages) {
        return new CriticResponse(IntStream.rangeClosed(0, pages)
                .mapToObj(n -> new PageVerdict(n, true, List.of())).toList());
    }

    @Test
    void cleanBookPasses() {
        llm.enqueue(allPass(10));
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                StoryFixtures.plan(10, "ذَهَبَ سامي إِلَى المَدْرَسَةِ."));

        assertThat(report.allPass()).isTrue();
        assertThat(llm.requests()).singleElement()
                .satisfies(r -> assertThat(r.purpose()).isEqualTo(LlmPurpose.STORY_CRITIC));
    }

    @Test
    void llmVerdictFailsOnlyThatPage() {
        List<PageVerdict> verdicts = new java.util.ArrayList<>(allPass(10).pages());
        verdicts.set(3, new PageVerdict(3, false, List.of("ذهبَ should be ذهبتْ for a girl")));
        llm.enqueue(new CriticResponse(verdicts));

        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.GIRL, 10),
                StoryFixtures.plan(10, "ذَهَبَتْ سامي إِلَى المَدْرَسَةِ."));

        assertThat(report.failingPages()).containsExactly(3);
        assertThat(report.problemsByPage().get(3)).containsExactly("ذهبَ should be ذهبتْ for a girl");
    }

    @Test
    void deterministicProblemsAreMergedWithLlmVerdicts() {
        llm.enqueue(allPass(10));
        StoryPlanResponse plan = StoryFixtures.plan(10, "ذَهَبَ سامي.");
        plan = plan.withPage(plan.pages().get(1).withText("لعب سامي football."));

        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), plan);

        assertThat(report.failingPages()).containsExactly(2);
        assertThat(report.problemsByPage().get(2)).anyMatch(p -> p.contains("Latin"));
    }

    @Test
    void theNameIsEnforcedBeforeReview() {
        llm.enqueue(allPass(10));
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                StoryFixtures.plan(10, "ذَهَبَ سَامِي إِلَى البَيْتِ."));

        assertThat(report.plan().pages()).allSatisfy(p -> assertThat(p.textAr()).contains("سامي"));
        assertThat(llm.requests().get(0).user()).doesNotContain("سَامِي");
    }

    @Test
    void aMissingVerdictCountsAsFailure() {
        llm.enqueue(new CriticResponse(List.of(new PageVerdict(0, true, List.of()))));
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                StoryFixtures.plan(10, "ذَهَبَ سامي."));

        assertThat(report.failingPages()).hasSize(10);
    }
}
