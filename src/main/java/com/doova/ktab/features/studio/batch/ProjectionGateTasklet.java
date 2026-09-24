package com.doova.ktab.features.studio.batch;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.features.ingestion.routing.BookContentPurger;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3.9 Projection quality gate.
 *
 * <p>Validates the projected digital book content:
 * <ul>
 *   <li>Chapter count within [minChapters, maxChapters]</li>
 *   <li>No chapter exceeding maxChapterCharRatio of total characters</li>
 *   <li>No empty chapters</li>
 * </ul>
 *
 * <p>On failure: purges generated content via {@link BookContentPurger},
 * records metric/log, overrides route to {@link IngestionRoute#OCR}, and fails the step.
 */
@Slf4j
@RequiredArgsConstructor
public class ProjectionGateTasklet implements Tasklet {

    private final Long bookId;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final BookPageRepository pageRepository;
    private final BookRepository bookRepository;
    private final BookContentPurger purger;
    private final StudioProperties props;
    private final MeterRegistry meterRegistry;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException("No live StudioProject found for bookId=" + bookId));

        List<StudioChapter> activeChapters = chapterRepository.findByProject_IdOrderByOrderIndexAsc(project.getId())
                .stream()
                .filter(c -> c.getDeletedAt() == null)
                .toList();

        StudioProperties.ProjectionGate gate = props.getProjectionGate();
        String failureReason = null;

        if (activeChapters.size() < gate.getMinChapters() || activeChapters.size() > gate.getMaxChapters()) {
            failureReason = "Chapter count " + activeChapters.size()
                    + " out of bounds [" + gate.getMinChapters() + ", " + gate.getMaxChapters() + "]";
        } else {
            long totalChars = 0;
            Map<Long, Long> chapterChars = new HashMap<>();

            for (StudioChapter chapter : activeChapters) {
                List<BookPage> pages = pageRepository
                        .findByBook_IdAndExternalChapterIdOrderByChapterPageOrdinalAsc(bookId, chapter.getExternalChapterId());

                long chars = 0;
                for (BookPage p : pages) {
                    String pageText = (p.getMarkdownClean() != null && !p.getMarkdownClean().isBlank())
                            ? p.getMarkdownClean()
                            : p.getMarkdownContent();
                    if (pageText != null) {
                        chars += pageText.length();
                    }
                }

                if (chars == 0) {
                    failureReason = "Empty chapter found: " + chapter.getExternalChapterId();
                    break;
                }

                chapterChars.put(chapter.getId(), chars);
                totalChars += chars;
            }

            if (failureReason == null && totalChars > 0) {
                for (Map.Entry<Long, Long> entry : chapterChars.entrySet()) {
                    double ratio = (double) entry.getValue() / totalChars;
                    if (ratio > gate.getMaxChapterCharRatio()) {
                        failureReason = "Chapter " + entry.getKey() + " has "
                                + String.format("%.2f%%", ratio * 100) + " of total characters (max: "
                                + String.format("%.2f%%", gate.getMaxChapterCharRatio() * 100) + ")";
                        break;
                    }
                }
            }
        }

        if (failureReason != null) {
            log.warn("studio.projection.gate.failed bookId={} reason={}", bookId, failureReason);
            meterRegistry.counter("studio.projection.gate", "result", "failed").increment();

            // Purge and reroute to OCR
            purger.purge(bookId);

            Book book = bookRepository.findById(bookId).orElse(null);
            if (book != null) {
                book.setIngestionRoute(IngestionRoute.OCR);
                book.setIngestionRouteLocked(true);
                book.setReviewNote("Projection gate failed: " + failureReason + "; rerouted to OCR.");
                bookRepository.save(book);
                meterRegistry.counter("ingestion.route.overridden", "to", IngestionRoute.OCR.name()).increment();
            }

            throw new IllegalStateException("Projection quality gate failed for bookId=" + bookId + ": " + failureReason);
        }

        log.info("studio.projection.gate.passed bookId={} chapters={}", bookId, activeChapters.size());
        meterRegistry.counter("studio.projection.gate", "result", "passed").increment();
        return RepeatStatus.FINISHED;
    }
}
