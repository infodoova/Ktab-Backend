package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.client.dto.StudioChapterDetail;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.enums.StudioChapterOrigin;
import com.doova.ktab.features.studio.enums.StudioProjectLifecycle;
import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Phase 4.1 Push path for OCR'd books.
 *
 * <p>For books that completed OCR and need audiobook generation:
 * creates one Studio chapter per {@link BookSection} via {@link ElevenLabsStudioClient#createChapter},
 * populating the chapter content with the section's clean markdown.
 *
 * <p>Writes {@code col_external_chapter_id} onto the section and creates a
 * {@link StudioChapter} row with origin {@link StudioChapterOrigin#KTAB_PUSHED}.
 */
@Slf4j
@RequiredArgsConstructor
public class PushChaptersTasklet implements Tasklet {

    private final Long bookId;
    private final ElevenLabsStudioClient client;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final BookSectionRepository sectionRepository;
    private final BookPageRepository pageRepository;
    private final StudioProperties props;
    private final TransactionTemplate transactionTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        if (props.getAudiobook().isDryRun()) {
            log.info("studio.pushChapters.dryRun bookId={} — skipping API push", bookId);
            return RepeatStatus.FINISHED;
        }

        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException("No live StudioProject found for bookId=" + bookId));
        String extProjectId = project.getExternalProjectId();

        List<StudioChapter> existingChapters = chapterRepository.findByProject_IdOrderByOrderIndexAsc(project.getId());
        if (!existingChapters.isEmpty() && existingChapters.stream().anyMatch(c -> c.getOrigin() == StudioChapterOrigin.STUDIO_PROJECTED)) {
            log.info("studio.pushChapters.skip bookId={} — digital project already has {} projected chapters",
                    bookId, existingChapters.size());
            return RepeatStatus.FINISHED;
        }

        List<BookSection> sections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);
        int orderIndex = 0;

        for (BookSection section : sections) {
            final int currentOrder = orderIndex++;

            // Idempotency check: if chapter was already created for this section, skip
            if (section.getExternalChapterId() != null) {
                Optional<StudioChapter> existing = chapterRepository
                        .findByProject_IdAndExternalChapterId(project.getId(), section.getExternalChapterId());
                if (existing.isPresent()) {
                    continue;
                }
            }

            // Read section pages outside transaction
            Integer startPage = section.getStartPage() != null ? section.getStartPage() : 1;
            Integer endPage = section.getEndPage() != null ? section.getEndPage() : startPage;

            List<BookPage> pages = pageRepository
                    .findByBookIdAndPageNumberBetweenOrderByPageNumberAsc(bookId, startPage, endPage);

            StringBuilder contentBuilder = new StringBuilder();
            for (BookPage p : pages) {
                String pageText = (p.getMarkdownClean() != null && !p.getMarkdownClean().isBlank())
                        ? p.getMarkdownClean()
                        : p.getMarkdownContent();
                if (pageText != null && !pageText.isBlank()) {
                    if (!contentBuilder.isEmpty()) contentBuilder.append("\n\n");
                    contentBuilder.append(pageText);
                }
            }
            String content = contentBuilder.toString();
            if (content.isBlank()) {
                content = section.getTitle() != null ? section.getTitle() : "Chapter " + (currentOrder + 1);
            }
            String title = section.getTitle() != null && !section.getTitle().isBlank()
                    ? section.getTitle() : "Chapter " + (currentOrder + 1);

            // External HTTP calls outside DB transaction (senior backend standard)
            // Step 1: Create the chapter (name only — API does not accept content at creation time)
            StudioChapterDetail detail = client.createChapter(extProjectId, title);

            // Step 2: Push OCR text content to the created chapter
            if (!content.isBlank()) {
                client.updateChapterContent(extProjectId, detail.chapterId(), content);
            }

            // Commit section and chapter link in a short transaction
            transactionTemplate.executeWithoutResult(status -> {
                section.setExternalChapterId(detail.chapterId());
                sectionRepository.save(section);

                StudioChapter chapter = new StudioChapter();
                chapter.setProject(project);
                chapter.setExternalChapterId(detail.chapterId());
                chapter.setBookSection(section);
                chapter.setOrigin(StudioChapterOrigin.KTAB_PUSHED);
                chapter.setOrderIndex(currentOrder);
                chapterRepository.save(chapter);
            });

            log.info("studio.pushChapters.pushed bookId={} sectionId={} extChapterId={}",
                    bookId, section.getId(), detail.chapterId());
        }

        transactionTemplate.executeWithoutResult(status -> {
            project.setLifecycle(StudioProjectLifecycle.SYNCED);
            projectRepository.save(project);
        });

        log.info("studio.pushChapters.done bookId={} sectionsCount={}", bookId, sections.size());
        return RepeatStatus.FINISHED;
    }
}
