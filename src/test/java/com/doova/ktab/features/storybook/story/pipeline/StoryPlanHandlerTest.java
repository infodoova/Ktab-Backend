package com.doova.ktab.features.storybook.story.pipeline;

import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.orchestrator.StepOutcome;
import com.doova.ktab.features.storybook.prompt.PromptLibrary;
import com.doova.ktab.features.storybook.story.StoryWriter;
import com.doova.ktab.features.storybook.story.SupportingCast;
import com.doova.ktab.features.storybook.support.FakeLlmGateway;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoryPlanHandlerTest {

    private final FakeLlmGateway llm = new FakeLlmGateway();
    private final StoryPersistence persistence = mock(StoryPersistence.class);
    private final AiCallLedger ledger = mock(AiCallLedger.class);
    private final StoryPlanHandler handler = new StoryPlanHandler(new StoryWriter(llm, new PromptLibrary()), persistence, ledger);

    private static StorybookJob job(int generation) {
        StorybookJob j = new StorybookJob();
        j.setId(7L);
        j.setStorybookId(42L);
        j.setStep(JobStep.STORY_PLAN);
        j.setGeneration(generation);
        return j;
    }

    private static StoryContext ctx(StorybookStatus status, boolean withPlan) {
        return new StoryContext(42L, status, StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10),
                withPlan ? StoryFixtures.plan(10, "x.") : null);
    }

    @Test
    void writesAndStoresAPlan() {
        when(persistence.load(42L)).thenReturn(ctx(StorybookStatus.DRAFT, false));
        llm.enqueue(StoryFixtures.plan(10, "ذَهَبَ سامي."));

        StepOutcome outcome = handler.handle(job(1));

        assertThat(outcome.type()).isEqualTo(StepOutcome.Type.SUCCESS);
        verify(ledger).recordLlm(eq(42L), eq(7L), eq(LlmPurpose.STORY_PLAN), any());
        verify(persistence).savePlan(eq(42L), any(), eq(1));
    }

    @Test
    void doesNotRewriteAStoredPlan() {
        when(persistence.load(42L)).thenReturn(ctx(StorybookStatus.DRAFT, true));

        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(llm.requests()).isEmpty();
        verify(persistence).enqueueCritic(42L, 0);
        verify(persistence, never()).savePlan(any(), any(), anyInt());
    }

    @Test
    void staleJobForAnApprovedBookDoesNothing() {
        when(persistence.load(42L)).thenReturn(ctx(StorybookStatus.STORY_READY, true));
        assertThat(handler.handle(job(0)).type()).isEqualTo(StepOutcome.Type.SUCCESS);
        assertThat(llm.requests()).isEmpty();
        verifyNoInteractions(ledger);
    }

    @Test
    void theWriterIsToldWhoTheSupportingCharactersAre() {
        SupportingCast grandpa = new SupportingCast("SUPPORT_1", "grandpa", "الجد", "grandfather", "Elder",
                "a brown jalabiya", "the grandfather, about 68 years old, a man");
        StoryContext base = new StoryContext(42L, StorybookStatus.DRAFT,
                StoryFixtures.request(LanguageVariety.MSA, ChildGender.BOY, 10), null);
        when(persistence.load(42L)).thenReturn(base.withSupporting(java.util.List.of(grandpa)));
        llm.enqueue(StoryFixtures.plan(10, "ذَهَبَ سامي."));

        handler.handle(job(0));

        assertThat(llm.requests().get(0).user()).contains("SUPPORT_1").contains("الجد").contains("grandfather");
    }
}
