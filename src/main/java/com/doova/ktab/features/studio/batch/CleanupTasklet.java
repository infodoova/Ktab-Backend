package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.client.StudioApiException;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Phase 4 {@code cleanupStep}: deletes the ElevenLabs Studio project remotely and tombstones
 * the local {@code tbl_studio_projects} row with {@code col_project_deleted_at}. Advances
 * lifecycle to {@code DELETED}.
 *
 * <p>The {@link com.doova.ktab.features.studio.model.BookAudioChapter} rows are the durable
 * product and are never touched by this step — cleanup only removes the transient ingestion
 * scaffolding. See docs/ocr_engine_v3.md, Phase 4 cleanupStep.
 *
 * <p>A 404 on the {@code DELETE} call is treated as a success (idempotent): the project may
 * have already been deleted by the orphan reconciler.
 */
@Slf4j
@RequiredArgsConstructor
public class CleanupTasklet implements Tasklet {

    private final Long bookId;
    private final ElevenLabsStudioClient client;
    private final StudioProjectRepository projectRepository;
    private final StudioProperties props;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "No live StudioProject found for bookId=" + bookId));

        if (project.getLifecycle() == StudioProjectLifecycle.DELETED) {
            log.info("studio.cleanup.skip bookId={} — already deleted", bookId);
            return RepeatStatus.FINISHED;
        }

        if (props.getAudiobook().isDryRun()) {
            log.info("studio.cleanup.dryRun bookId={} — skipping remote delete", bookId);
            return RepeatStatus.FINISHED;
        }

        try {
            client.deleteProject(project.getExternalProjectId());
        } catch (StudioApiException.Fatal ex) {
            // 404 → already gone; treat as success. Any other 4xx is re-thrown.
            if (ex.statusCode() == 404) {
                log.info("studio.cleanup.alreadyDeleted bookId={} extId={}",
                        bookId, project.getExternalProjectId());
            } else {
                throw ex;
            }
        }

        project.setLifecycle(StudioProjectLifecycle.DELETED);
        project.setProjectDeletedAt(Instant.now());
        projectRepository.save(project);

        log.info("studio.cleanup.done bookId={} extId={}", bookId, project.getExternalProjectId());
        return RepeatStatus.FINISHED;
    }
}

