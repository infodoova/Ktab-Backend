package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.enums.StudioProjectLifecycle;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Phase 4 {@code convertStep}: fires {@code POST /convert} once and advances
 * {@code col_lifecycle} to {@code CONVERTING}. Returns immediately — conversion is
 * asynchronous; {@link PollTasklet} polls for completion.
 *
 * <p>Idempotent: if the project is already {@code CONVERTING} (e.g. batch restart),
 * the step skips the API call and moves on. See docs/ocr_engine_v3.md, Phase 4.2.
 */
@Slf4j
@RequiredArgsConstructor
public class ConvertTasklet implements Tasklet {

    private final Long bookId;
    private final ElevenLabsStudioClient client;
    private final StudioProjectRepository projectRepository;
    private final StudioProperties props;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "No live StudioProject found for bookId=" + bookId));

        if (project.getLifecycle() == StudioProjectLifecycle.CONVERTING
                || project.getLifecycle() == StudioProjectLifecycle.CONVERTED) {
            log.info("studio.convert.skip bookId={} lifecycle={} — already converting/converted",
                    bookId, project.getLifecycle());
            return RepeatStatus.FINISHED;
        }

        if (props.getAudiobook().isDryRun()) {
            log.info("studio.convert.dryRun bookId={} — skipping /convert call", bookId);
            return RepeatStatus.FINISHED;
        }

        client.convertProject(project.getExternalProjectId());

        project.setLifecycle(StudioProjectLifecycle.CONVERTING);
        projectRepository.save(project);

        log.info("studio.convert.done bookId={} extProjectId={}", bookId, project.getExternalProjectId());
        return RepeatStatus.FINISHED;
    }
}

