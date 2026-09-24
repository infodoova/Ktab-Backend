package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobOutcomeRecorderTest {

    private final StorybookJobRepository jobs = mock(StorybookJobRepository.class);
    private final StorybookRepository books = mock(StorybookRepository.class);
    private final StorybookProperties props = new StorybookProperties(); // maxAttempts 5
    private final JobOutcomeRecorder recorder = new JobOutcomeRecorder(jobs, books, new StorybookStateMachine(),
            new BackoffPolicy(props, () -> 1.0), props);

    private StorybookJob job;
    private Storybook book;

    @BeforeEach
    void setUp() {
        job = new StorybookJob();
        job.setId(1L);
        job.setStorybookId(10L);
        job.setStep(JobStep.ILLUSTRATE_PAGE);
        job.setStatus(JobStatus.RUNNING);
        job.setLockedBy("w");
        book = new Storybook();
        book.setStatus(StorybookStatus.ILLUSTRATING);
        when(jobs.findById(1L)).thenReturn(Optional.of(job));
        when(books.findById(10L)).thenReturn(Optional.of(book));
    }

    @Test
    void successFinishesTheJob() {
        recorder.record(1L, StepOutcome.success());
        assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getLockedBy()).isNull();
    }

    @Test
    void retryReschedulesWithBackoff() {
        job.setAttempts(2);
        Instant before = Instant.now();
        recorder.record(1L, StepOutcome.retry("HTTP 503"));
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getLastError()).isEqualTo("HTTP 503");
        assertThat(job.getNextRunAt()).isAfterOrEqualTo(before.plus(Duration.ofSeconds(20)));
        assertThat(book.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
    }

    @Test
    void retryAtTheLimitKillsTheJobAndFailsTheBook() {
        job.setAttempts(5);
        recorder.record(1L, StepOutcome.retry("HTTP 503"));
        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);
        assertThat(book.getStatus()).isEqualTo(StorybookStatus.FAILED);
        assertThat(book.getFailedFromStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(book.getFailureReason()).contains("ILLUSTRATE_PAGE").contains("HTTP 503");
    }

    @Test
    void failIsImmediate() {
        job.setAttempts(1);
        recorder.record(1L, StepOutcome.fail("refused"));
        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);
        assertThat(book.getStatus()).isEqualTo(StorybookStatus.FAILED);
    }
}
