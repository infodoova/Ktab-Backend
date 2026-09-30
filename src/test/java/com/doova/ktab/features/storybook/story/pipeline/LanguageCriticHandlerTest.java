package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.story.CriticResponse;
import com.doova.ktab.features.storybook.story.LanguageCritic;
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.PageVerdict;
import com.doova.ktab.features.storybook.story.StoryPlanResponse;
import com.doova.ktab.features.storybook.story.StoryWriter;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LanguageCriticHandlerTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final PromptLibrary prompts = new PromptLibrary();
    private final StoryPersistence persistence = mock(StoryPersistence.class);
    private final LanguageCriticHandler handler = new LanguageCriticHandler(
            new LanguageCritic(llm, prompts),
            new StoryWriter(llm, prompts),
            persistence,
            mock(AiCallLedger.class),
            new StorybookProperties()
    );

    private static final String GOOD = "ذَهَبَ سامي إِلَى المَدْرَسَةِ.";

    private static StorybookJob job(int generation) {
        StorybookJob j = new StorybookJob();
        j.setId(20L);
        j.setStorybookId(42L);
        j.setStep(JobStep.LANGUAGE_CRITIC);
        j.setGeneration(generation);
        return j;
    }

    private void stored(StoryPlanResponse plan) {
        when(persistence.load(42L)).thenReturn(new StoryContext(42L, StorybookStatus.DRAFT,
                StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), plan));
    }

    private static CriticResponse verdicts(int failingPage) {
        List<PageVerdict> v = new ArrayList<>(IntStream.rangeClosed(0, 10)
                .mapToObj(n -> new PageVerdict(n, true, List.of())).toList());
        if (failingPage >= 0) {
            v.set(failingPage, new PageVerdict(failingPage, false, List.of("dialect mismatch")));
        }
        return new CriticResponse(v);
    }

    @Test
    void cleanStoryIsAcceptedAndTransitionsToStoryReady() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(-1));

        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).acceptStory(eq(42L), any());
    }

    @Test
    void failingLanguagePageIsRewrittenAndThenAccepted() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(3));
        llm.enqueue(new PagePlan(3, "ذَهَبَ سامي إِلَى الحَدِيقَةِ.", "scene", List.of(), com.doova.ktab.features.storybook.enums.TextZone.BOTTOM));
        llm.enqueue(verdicts(-1));

        handler.handle(job(0));

        ArgumentCaptor<StoryPlanResponse> accepted = ArgumentCaptor.forClass(StoryPlanResponse.class);
        verify(persistence).acceptStory(eq(42L), accepted.capture());
        assertThat(accepted.getValue().pages().get(2).textAr()).isEqualTo("ذَهَبَ سامي إِلَى الحَدِيقَةِ.");
    }
}
