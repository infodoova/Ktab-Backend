package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.model.StorybookJob;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JobWorkerCostGuardTest {

    @Test
    void overBudgetBooksFailInsteadOfCallingTheHandler() {
        JobOutcomeRecorder recorder = mock(JobOutcomeRecorder.class);
        StorybookCostGuard guard = mock(StorybookCostGuard.class);
        when(guard.exceeded(9L)).thenReturn(true);
        AtomicBoolean called = new AtomicBoolean();
        StepHandler handler = new StepHandler() {
            public JobStep step() { return JobStep.ILLUSTRATE_PAGE; }
            public StepOutcome handle(StorybookJob job) { called.set(true); return StepOutcome.success(); }
        };
        JobWorker worker = new JobWorker(mock(JobClaimer.class), recorder, new StepHandlerRegistry(List.of(handler)),
                new SyncTaskExecutor(), new StorybookProperties(), guard);
        StorybookJob job = new StorybookJob();
        job.setId(1L);
        job.setStorybookId(9L);
        job.setStep(JobStep.ILLUSTRATE_PAGE);

        worker.runOne(job);

        assertThat(called).isFalse();
        verify(recorder).record(eq(1L), argThat(o -> o.type() == StepOutcome.Type.FAIL && o.reason().contains("6.00")));
    }
}
