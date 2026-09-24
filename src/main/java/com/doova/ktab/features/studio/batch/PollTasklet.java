package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.enums.StudioProjectLifecycle;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.features.studio.sync.StudioSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Phase 4 {@code pollStep}: polls {@link StudioSyncService#syncStatus} with exponential
 * backoff until all chapters report full conversion progress or the wall-clock timeout
 * ({@code ktab.studio.sync.poll-timeout}) is reached.
 *
 * <p>Returns {@link RepeatStatus#CONTINUABLE} between polls so that Spring Batch
 * checkpoints the step execution between iterations — a restart resumes polling rather than
 * re-firing {@code /convert}. Returns {@link RepeatStatus#FINISHED} when all chapters are
 * converted or the project is in a terminal state. See docs/ocr_engine_v3.md, Phase 4.2.
 *
 * <h3>Backoff strategy</h3>
 * Starts at {@code pollIntervalMin} (default 3 s), doubles on each iteration up to
 * {@code pollIntervalMax} (default 30 s) — no jitter needed here because only one poller
 * runs per project (enforced by the pessimistic lock in {@code syncStatus}).
 */
@Slf4j
@RequiredArgsConstructor
public class PollTasklet implements Tasklet {

    private final Long bookId;
    private final StudioSyncService syncService;
    private final StudioProjectRepository projectRepository;
    private final StudioProperties props;

    /** Wall-clock start; set on first execute() call. */
    private Instant started;
    /** Current backoff; doubles each iteration up to pollIntervalMax. */
    private Duration currentInterval;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (started == null) {
            started = Instant.now();
            currentInterval = props.getSync().getPollIntervalMin();
        }

        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "No live StudioProject found for bookId=" + bookId));

        if (project.getLifecycle().isTerminal()) {
            throw new IllegalStateException(
                    "Project reached terminal lifecycle during polling: " + project.getLifecycle()
                            + " bookId=" + bookId);
        }

        // Timeout guard
        Duration elapsed = Duration.between(started, Instant.now());
        Duration timeout = props.getSync().getPollTimeout();
        if (elapsed.compareTo(timeout) > 0) {
            throw new IllegalStateException(
                    "Conversion poll timeout after " + elapsed.toMinutes() + " min for bookId=" + bookId
                            + ". Increase ktab.studio.sync.poll-timeout or investigate the project.");
        }

        boolean allConverted = syncService.syncStatus(project.getId());

        if (allConverted) {
            project.setLifecycle(StudioProjectLifecycle.CONVERTED);
            projectRepository.save(project);
            log.info("studio.poll.done bookId={} elapsed={}s", bookId, elapsed.toSeconds());
            return RepeatStatus.FINISHED;
        }

        // Back off and signal Batch to checkpoint + loop
        log.debug("studio.poll.waiting bookId={} elapsed={}s next={}ms",
                bookId, elapsed.toSeconds(), currentInterval.toMillis());
        Thread.sleep(currentInterval.toMillis());

        // Exponential backoff: double up to max
        Duration maxInterval = props.getSync().getPollIntervalMax();
        currentInterval = currentInterval.multipliedBy(2);
        if (currentInterval.compareTo(maxInterval) > 0) {
            currentInterval = maxInterval;
        }

        return RepeatStatus.CONTINUABLE;
    }
}

