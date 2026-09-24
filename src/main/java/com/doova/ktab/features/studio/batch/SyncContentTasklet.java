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

/**
 * Phase 4 {@code syncContentStep}: runs Tier 2 content sync after project creation so that
 * the book's section and page structure is projected from Studio's chapter parse before
 * conversion starts. See docs/ocr_engine_v3.md, Phase 4.2 syncContentStep.
 *
 * <p>For the OCR-push path, this step is replaced by {@code pushChaptersStep} (not in this
 * job — the push path is a separate audiobook-only job for OCR-route books).
 */
@Slf4j
@RequiredArgsConstructor
public class SyncContentTasklet implements Tasklet {

    private final Long bookId;
    private final StudioSyncService syncService;
    private final StudioProjectRepository projectRepository;
    private final StudioProperties props;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (props.getAudiobook().isDryRun()) {
            log.info("studio.syncContent.dryRun bookId={} — skipping", bookId);
            return RepeatStatus.FINISHED;
        }

        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "No live StudioProject found for bookId=" + bookId));

        syncService.syncContent(project.getId());

        project.setLifecycle(StudioProjectLifecycle.SYNCED);
        projectRepository.save(project);

        log.info("studio.syncContent.done bookId={} projectId={}", bookId, project.getId());
        return RepeatStatus.FINISHED;
    }
}

