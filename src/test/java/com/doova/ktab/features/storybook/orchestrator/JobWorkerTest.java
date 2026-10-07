package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.image.ImageGenerationException;
import com.doova.ktab.features.storybook.llm.LlmCallFailedException;
import com.doova.ktab.features.storybook.model.StorybookJob;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.List;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobWorkerTest {

    private final JobClaimer claimer = mock(JobClaimer.class);
    private final JobOutcomeRecorder recorder = mock(JobOutcomeRecorder.class);

    private JobWorker worker(Function<StorybookJob, StepOutcome> behaviour) {
        StepHandler handler = new StepHandler() {
            public JobStep step() { return JobStep.STORY_PLAN; }
            public StepOutcome handle(StorybookJob job) { return behaviour.apply(job); }
        };
        return new JobWorker(claimer, recorder, new StepHandlerRegistry(List.of(handler)),
                new SyncTaskExecutor(), new StorybookProperties(), mock(StorybookCostGuard.class));
    }

    private static StorybookJob job(JobStep step) {
        StorybookJob j = new StorybookJob();
        j.setId(1L);
        j.setStep(step);
        return j;
    }

    @Test
    void successIsRecorded() {
        worker(j -> StepOutcome.success()).runOne(job(JobStep.STORY_PLAN));
        verify(recorder).record(eq(1L), argThat(o -> o.type() == StepOutcome.Type.SUCCESS));
    }

    @Test
    void retryableProviderErrorsAreRetried() {
        worker(j -> { throw new ImageGenerationException("503", true, null); }).runOne(job(JobStep.STORY_PLAN));
        verify(recorder).record(eq(1L), argThat(o -> o.type() == StepOutcome.Type.RETRY));
    }

    @Test
    void nonRetryableLlmErrorsFail() {
        worker(j -> { throw new LlmCallFailedException("refused", false, null); }).runOne(job(JobStep.STORY_PLAN));
        verify(recorder).record(eq(1L), argThat(o -> o.type() == StepOutcome.Type.FAIL));
    }

    @Test
    void unexpectedBugsAreRetriedNotLoopedForever() {
        worker(j -> { throw new NullPointerException("oops"); }).runOne(job(JobStep.STORY_PLAN));
        verify(recorder).record(eq(1L), argThat(o -> o.type() == StepOutcome.Type.RETRY && o.reason().contains("oops")));
    }

    @Test
    void missingHandlerFails() {
        worker(j -> StepOutcome.success()).runOne(job(JobStep.RENDER_PDF));
        verify(recorder).record(eq(1L), argThat(o -> o.type() == StepOutcome.Type.FAIL));
    }

    @Test
    void pollClaimsOnlyFreeSlots() {
        when(claimer.claim(anyString(), anyInt())).thenReturn(List.of());
        worker(j -> StepOutcome.success()).poll();
        verify(claimer).releaseStale(new StorybookProperties().getWorker().getLease());
        verify(claimer).claim(anyString(), eq(4));
    }

    @Test
    void runOne_whenTransactionCreationFailsDuringShutdown_doesNotThrow() {
        doThrow(new org.springframework.transaction.CannotCreateTransactionException("EntityManagerFactory is closed"))
                .when(recorder).record(anyLong(), any(StepOutcome.class));

        // Should log warning and not throw uncaught exception to caller/thread
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                worker(j -> StepOutcome.success()).runOne(job(JobStep.STORY_PLAN)));
    }
}
