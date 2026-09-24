package com.doova.ktab.features.storybook.orchestrator;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.image.ImageGenerationException;
import com.doova.ktab.features.storybook.llm.LlmCallFailedException;
import com.doova.ktab.features.storybook.model.StorybookJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "ktab.storybook", name = "enabled", havingValue = "true")
public class JobWorker {

    private final JobClaimer claimer;
    private final JobOutcomeRecorder recorder;
    private final StepHandlerRegistry handlers;
    private final TaskExecutor executor;
    private final StorybookProperties properties;
    private final StorybookCostGuard costGuard;
    private final String workerId;
    private final AtomicInteger inFlight = new AtomicInteger();

    public JobWorker(JobClaimer claimer, JobOutcomeRecorder recorder, StepHandlerRegistry handlers,
                     @Qualifier("storybookJobExecutor") TaskExecutor executor, StorybookProperties properties,
                     StorybookCostGuard costGuard) {
        this.claimer = claimer;
        this.recorder = recorder;
        this.handlers = handlers;
        this.executor = executor;
        this.properties = properties;
        this.costGuard = costGuard;
        this.workerId = hostname() + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelayString = "${ktab.storybook.worker.poll-delay:2s}")
    public void poll() {
        claimer.releaseStale(properties.getWorker().getLease());
        int free = properties.getWorker().getConcurrency() - inFlight.get();
        if (free <= 0) {
            return;
        }
        List<StorybookJob> claimed = claimer.claim(workerId, free);
        for (StorybookJob job : claimed) {
            inFlight.incrementAndGet();
            try {
                executor.execute(() -> {
                    try {
                        runOne(job);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                });
            } catch (RuntimeException rejected) {
                inFlight.decrementAndGet();
                recorder.record(job.getId(), StepOutcome.retry("executor rejected: " + rejected.getMessage()));
            }
        }
    }

    void runOne(StorybookJob job) {
        StepOutcome outcome;
        StepHandler handler = handlers.get(job.getStep());
        if (job.getStep() != com.doova.ktab.features.storybook.enums.JobStep.PURGE_PHOTO
                && costGuard.exceeded(job.getStorybookId())) {
            log.error("storybook book {} exceeded its AI cost cap; failing job {} ({})",
                    job.getStorybookId(), job.getId(), job.getStep());
            outcome = StepOutcome.fail("Book AI cost exceeded $" + properties.getLimits().getMaxBookCostUsd());
        } else if (handler == null) {
            outcome = StepOutcome.fail("No handler for " + job.getStep());
        } else {
            try {
                outcome = handler.handle(job);
            } catch (LlmCallFailedException e) {
                outcome = e.retryable() ? StepOutcome.retry(e.getMessage()) : StepOutcome.fail(e.getMessage());
            } catch (ImageGenerationException e) {
                outcome = e.retryable() ? StepOutcome.retry(e.getMessage()) : StepOutcome.fail(e.getMessage());
            } catch (RuntimeException e) {
                log.error("storybook job {} ({}) threw", job.getId(), job.getStep(), e);
                outcome = StepOutcome.retry(e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        recorder.record(job.getId(), outcome);
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
}
