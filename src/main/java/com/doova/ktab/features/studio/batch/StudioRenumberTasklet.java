package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 3.6 post-projection renumbering pass.
 *
 * <p>Assigns contiguous 1..N {@code col_page_number} across all synthetic pages
 * in chapter sequence (ordered by {@code col_order_index ASC}), sets
 * {@code col_start_page} and {@code col_end_page} on each {@link BookSection},
 * and updates {@code tbl_books.col_page_count}.
 */
@Slf4j
@RequiredArgsConstructor
public class StudioRenumberTasklet implements Tasklet {

    private final Long bookId;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final BookPageRepository pageRepository;
    private final BookSectionRepository sectionRepository;
    private final BookRepository bookRepository;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException("No live StudioProject found for bookId=" + bookId));

        List<StudioChapter> chapters = chapterRepository.findByProject_IdOrderByOrderIndexAsc(project.getId());

        int globalPageNumber = 1;

        for (StudioChapter chapter : chapters) {
            if (chapter.getDeletedAt() != null) continue;

            List<BookPage> pages = pageRepository
                    .findByBook_IdAndExternalChapterIdOrderByChapterPageOrdinalAsc(bookId, chapter.getExternalChapterId());

            if (pages.isEmpty()) {
                log.warn("studio.renumber.emptyChapter bookId={} chapterId={}", bookId, chapter.getId());
                continue;
            }

            int startPage = globalPageNumber;
            for (BookPage page : pages) {
                page.setPageNumber(globalPageNumber++);
                pageRepository.save(page);
            }
            int endPage = globalPageNumber - 1;

            BookSection section = chapter.getBookSection();
            if (section != null) {
                section.setStartPage(startPage);
                section.setEndPage(endPage);
                section.setSortOrder(chapter.getOrderIndex() + 1);
                sectionRepository.save(section);
            }
        }

        int totalPages = globalPageNumber - 1;
        Book book = project.getBook();
        book.setPageCount(totalPages);
        bookRepository.save(book);

        log.info("studio.renumber.done bookId={} totalPages={}", bookId, totalPages);
        return RepeatStatus.FINISHED;
    }
}
