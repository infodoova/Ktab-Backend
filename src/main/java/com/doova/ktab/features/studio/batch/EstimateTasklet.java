package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.repository.book.BookPageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

/**
 * Phase 4 {@code estimateStep}: counts total characters across all book pages and
 * hard-fails if the character count exceeds {@code ktab.studio.audiobook.max-chars-per-book}.
 * This is the cost guard described in docs/ocr_engine_v3.md, Phase 4.4.
 *
 * <p>The step is placed first so that the {@code /convert} call is never reached for
 * books that would immediately exceed quota. A failed step here does not persist any
 * Studio state — nothing to roll back or reconcile.
 */
@Slf4j
@RequiredArgsConstructor
public class EstimateTasklet implements Tasklet {

    private final Long bookId;
    private final BookPageRepository pageRepository;
    private final StudioProperties props;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        int totalChars = pageRepository.getTotalWordCount(bookId);
        // getTotalWordCount counts words; character estimate = words × avg 5 chars. Use the
        // full markdown content word count as a proxy — a more precise character scan would
        // require streaming all page content. This is intentionally conservative.
        long charEstimate = (long) totalChars * 5;

        long ceiling = props.getAudiobook().getMaxCharsPerBook();

        log.info("studio.estimate bookId={} wordCount={} charEstimate={} ceiling={}",
                bookId, totalChars, charEstimate, ceiling);

        if (charEstimate > ceiling) {
            throw new IllegalStateException(
                    "Book " + bookId + " exceeds max-chars-per-book ceiling: "
                            + charEstimate + " > " + ceiling
                            + ". Set ktab.studio.audiobook.max-chars-per-book to override.");
        }

        if (props.getAudiobook().isDryRun()) {
            log.info("studio.estimate.dryRun bookId={} — dry-run mode, stopping after estimate", bookId);
            // Signal downstream steps to skip — dry-run flag is checked in each tasklet
        }

        return RepeatStatus.FINISHED;
    }
}

