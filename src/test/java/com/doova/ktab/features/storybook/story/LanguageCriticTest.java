package com.doova.ktab.features.storybook.story;

import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageCriticTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final PromptLibrary prompts = new PromptLibrary();
    private final LanguageCritic critic = new LanguageCritic(llm, prompts);

    @Test
    void flagsDigitsInArabicStoryText() {
        llm.enqueue(new CriticResponse(List.of(new PageVerdict(1, true, List.of()))));

        StoryPlanResponse plan = StoryFixtures.plan(1, "رأى سامي 3 عصافير.");
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 1), plan);

        assertThat(report.allPass()).isFalse();
        assertThat(report.problemsByPage().get(1))
                .anyMatch(p -> p.contains("digits") && p.contains("Arabic words"));
    }

    @Test
    void flagsTashkeelInDialectStoryText() {
        llm.enqueue(new CriticResponse(List.of(new PageVerdict(1, true, List.of()))));

        StoryPlanResponse plan = StoryFixtures.plan(1, "رَاح سامي عالبَيْت.");
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.EGYPTIAN, ChildGender.BOY, 1), plan);

        assertThat(report.allPass()).isFalse();
        assertThat(report.problemsByPage().get(1))
                .anyMatch(p -> p.contains("tashkeel"));
    }

    @Test
    void cleanTextPassesDeterministicAndLlmChecks() {
        llm.enqueue(new CriticResponse(List.of(new PageVerdict(1, true, List.of()))));

        StoryPlanResponse plan = StoryFixtures.plan(1, "رأى سامي ثلاثة عصافير تغرد.");
        CriticReport report = critic.review(StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 1), plan);

        assertThat(report.allPass()).isTrue();
    }
}
