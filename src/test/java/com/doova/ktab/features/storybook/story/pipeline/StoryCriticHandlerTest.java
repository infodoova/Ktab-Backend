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
import com.doova.ktab.features.storybook.story.PagePlan;
import com.doova.ktab.features.storybook.story.PageVerdict;
import com.doova.ktab.features.storybook.story.StoryCritic;
import com.doova.ktab.features.storybook.story.TitleRewriteResponse;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoryCriticHandlerTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final PromptLibrary prompts = new PromptLibrary();
    private final StoryPersistence persistence = mock(StoryPersistence.class);
    private final StoryCriticHandler handler = new StoryCriticHandler(new StoryCritic(llm, prompts),
            new StoryWriter(llm, prompts), persistence, mock(AiCallLedger.class), new StorybookProperties());

    private static final String GOOD = "ذَهَبَ سامي إِلَى المَدْرَسَةِ.";

    private static StorybookJob job(int generation) {
        StorybookJob j = new StorybookJob();
        j.setId(1L);
        j.setStorybookId(42L);
        j.setStep(JobStep.STORY_CRITIC);
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
            v.set(failingPage, new PageVerdict(failingPage, false, List.of("gender agreement")));
        }
        return new CriticResponse(v);
    }

    @Test
    void cleanStoryIsAccepted() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(-1));

        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).enqueueLanguageCritic(eq(42L), eq(0));
    }

    @Test
    void aFailingPageIsRewrittenOnItsOwn() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(4));                                     // first review: page 4 fails
        llm.enqueue(new PagePlan(4, "ذَهَبَ سامي إِلَى البَيْتِ.", "scene", List.of(), com.doova.ktab.features.storybook.enums.TextZone.TOP)); // rewrite
        llm.enqueue(verdicts(-1));                                    // second review: all pass

        handler.handle(job(0));

        ArgumentCaptor<StoryPlanResponse> saved = ArgumentCaptor.forClass(StoryPlanResponse.class);
        verify(persistence).saveRewrittenPages(eq(42L), saved.capture());
        verify(persistence).enqueueLanguageCritic(eq(42L), eq(0));
        assertThat(saved.getValue().pages().get(3).textAr()).isEqualTo("ذَهَبَ سامي إِلَى البَيْتِ.");
        assertThat(llm.requests()).hasSize(3);
    }

    @Test
    void aPageStillFlaggedAfterItsRewritesIsAcceptedAsRewritten() {
        stored(StoryFixtures.plan(10, GOOD));
        PagePlan rewrite = new PagePlan(4, GOOD, "scene", List.of(), com.doova.ktab.features.storybook.enums.TextZone.TOP);
        llm.enqueue(verdicts(4)); llm.enqueue(rewrite);   // round 1
        llm.enqueue(verdicts(4)); llm.enqueue(rewrite);   // round 2
        llm.enqueue(verdicts(4));                         // the editor still nitpicks

        handler.handle(job(0));

        verify(persistence).saveRewrittenPages(eq(42L), any());
        verify(persistence).enqueueLanguageCritic(eq(42L), eq(0));
        verify(persistence, never()).restartPlan(any(), anyInt(), anyMap());
        verify(persistence, never()).failStory(any(), any());
        // what the editor still objected to is kept on the page, not thrown away
        verify(persistence).recordUnresolvedProblems(eq(42L), eq("story"), argThat(m -> m.containsKey(4)), argThat(r -> r.contains(4)));
    }

    @Test
    void aFlaggedTitleIsRewrittenInPlaceAndTheStoryIsNotRestarted() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(0));                                           // the editor flags the title
        llm.enqueue(new TitleRewriteResponse("خَرِيطَةُ النُّجُومِ الْمَنْسِيَّةُ")); // the title alone is rewritten
        llm.enqueue(verdicts(-1));                                          // now everything passes

        handler.handle(job(0));

        ArgumentCaptor<StoryPlanResponse> saved = ArgumentCaptor.forClass(StoryPlanResponse.class);
        verify(persistence).saveRewrittenPages(eq(42L), saved.capture());
        assertThat(saved.getValue().titleAr()).isEqualTo("خَرِيطَةُ النُّجُومِ الْمَنْسِيَّةُ");
        verify(persistence).enqueueLanguageCritic(eq(42L), eq(0));
        verify(persistence, never()).restartPlan(any(), anyInt(), anyMap());
        verify(persistence, never()).failStory(any(), any());
    }

    @Test
    void theStoryRestartsOnlyWhenRewritingIsSwitchedOff() {
        StorybookProperties noRewrites = new StorybookProperties();
        noRewrites.getLimits().setCriticRewritesPerPage(0);
        StoryCriticHandler strict = new StoryCriticHandler(new StoryCritic(llm, prompts), new StoryWriter(llm, prompts),
                persistence, mock(AiCallLedger.class), noRewrites);
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(4));

        strict.handle(job(0));

        verify(persistence).restartPlan(eq(42L), eq(1), anyMap());
    }

    @Test
    void givesUpAfterThreePlansWhenRewritingIsSwitchedOff() {
        StorybookProperties noRewrites = new StorybookProperties();
        noRewrites.getLimits().setCriticRewritesPerPage(0);
        StoryCriticHandler strict = new StoryCriticHandler(new StoryCritic(llm, prompts), new StoryWriter(llm, prompts),
                persistence, mock(AiCallLedger.class), noRewrites);
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(4));

        assertThat(strict.handle(job(2)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(persistence).failStory(eq(42L), contains("gender agreement"));
        verify(persistence, never()).restartPlan(any(), anyInt(), anyMap());
    }

    @Test
    void staleJobDoesNothing() {
        when(persistence.load(42L)).thenReturn(new StoryContext(42L, StorybookStatus.DRAFT,
                StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), null));
        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(llm.requests()).isEmpty();
    }

    @Test
    void aCleanStoryRecordsNoComplaints() {
        stored(StoryFixtures.plan(10, GOOD));
        llm.enqueue(verdicts(-1));

        handler.handle(job(0));

        verify(persistence, never()).recordUnresolvedProblems(any(), any(), any(), any());
    }
}
