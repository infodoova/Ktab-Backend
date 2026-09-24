package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.enums.StudioProjectLifecycle;
import com.doova.ktab.features.studio.model.BookAudioChapter;
import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.BookAudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 4 {@code finalizeStep}: reads the download metadata written into the job execution
 * context by {@link DownloadTasklet} and upserts {@code tbl_book_audio_chapters} rows — the
 * durable, vendor-neutral audiobook product. Advances lifecycle to {@code FINALIZED}.
 *
 * <p>Idempotent: upserts keyed on {@code (col_book_id, col_sort_order)} — re-running after
 * a crash updates the existing row rather than creating a duplicate.
 * See docs/ocr_engine_v3.md, Phase 4.3.
 */
@Slf4j
@RequiredArgsConstructor
public class FinalizeTasklet implements Tasklet {

    private final Long bookId;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final BookAudioChapterRepository audioChapterRepository;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "No live StudioProject found for bookId=" + bookId));

        List<StudioChapter> chapters =
                chapterRepository.findByProject_IdOrderByOrderIndexAsc(project.getId());

        ExecutionContext ctx = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();

        int written = 0;
        for (StudioChapter chapter : chapters) {
            if (chapter.getDeletedAt() != null) continue;

            Long chapterId = chapter.getId();
            String r2Key      = (String) ctx.get("chapter." + chapterId + ".r2Key");
            String sha256      = (String) ctx.get("chapter." + chapterId + ".sha256");
            Long sizeBytes     = (Long)   ctx.get("chapter." + chapterId + ".sizeBytes");
            Long durationMs    = (Long)   ctx.get("chapter." + chapterId + ".durationMs");
            Integer sortOrder  = (Integer) ctx.get("chapter." + chapterId + ".sortOrder");

            if (r2Key == null) {
                log.warn("studio.finalize.missingContext chapterId={} — no download result, skipping", chapterId);
                continue;
            }

            // Timing index path from TimingIndexTasklet context, with gzipped fallback
            String timingsKey = (String) ctx.get("chapter." + chapterId + ".timingsKey");
            if (timingsKey == null) {
                timingsKey = r2Key.replace("chapters/ch-", "timings/ch-").replace(".mp3", ".json.gz");
            }

            // Upsert keyed on (bookId, sortOrder)
            BookAudioChapter audio = audioChapterRepository
                    .findByBook_IdAndSortOrder(bookId, sortOrder)
                    .orElseGet(BookAudioChapter::new);

            audio.setBook(chapter.getProject().getBook());
            audio.setBookSection(chapter.getBookSection());
            audio.setSortOrder(sortOrder);
            audio.setAudioPath(r2Key);
            audio.setTimingsPath(timingsKey);
            audio.setSizeBytes(sizeBytes);
            audio.setSha256(sha256);
            audio.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));

            audioChapterRepository.save(audio);
            written++;
        }

        project.getBook().setHasAudio(true);
        project.setLifecycle(StudioProjectLifecycle.CONVERTED);
        projectRepository.save(project);

        log.info("studio.finalize.done bookId={} chapters={}", bookId, written);
        return RepeatStatus.FINISHED;
    }
}

