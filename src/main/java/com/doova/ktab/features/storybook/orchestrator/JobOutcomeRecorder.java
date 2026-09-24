package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.JobStatus;
import com.doova.ktab.features.storybook.model.StorybookJob;
import com.doova.ktab.features.storybook.repository.StorybookJobRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobOutcomeRecorder {

    private final StorybookJobRepository jobs;
    private final StorybookRepository books;
    private final StorybookStateMachine stateMachine;
    private final BackoffPolicy backoff;
    private final StorybookProperties properties;

    @Transactional
    public void record(Long jobId, StepOutcome outcome) {
        StorybookJob job = jobs.findById(jobId).orElseThrow();
        job.setLockedBy(null);
        job.setLockedAt(null);
        switch (outcome.type()) {
            case SUCCESS -> {
                job.setStatus(JobStatus.SUCCEEDED);
                job.setFinishedAt(Instant.now());
                job.setLastError(null);
            }
            case RETRY -> {
                if (job.getAttempts() < properties.getWorker().getMaxAttempts()) {
                    job.setStatus(JobStatus.PENDING);
                    job.setNextRunAt(Instant.now().plus(backoff.delayAfter(job.getAttempts())));
                    job.setLastError(outcome.reason());
                } else {
                    kill(job, outcome.reason());
                }
            }
            case FAIL -> kill(job, outcome.reason());
        }
    }

    private void kill(StorybookJob job, String reason) {
        job.setStatus(JobStatus.DEAD);
        job.setFinishedAt(Instant.now());
        job.setLastError(reason);
        log.warn("storybook job {} ({}) for book {} is dead: {}", job.getId(), job.getStep(), job.getStorybookId(), reason);
        books.findById(job.getStorybookId())
                .ifPresent(book -> stateMachine.fail(book, job.getStep() + " failed: " + reason));
    }
}
