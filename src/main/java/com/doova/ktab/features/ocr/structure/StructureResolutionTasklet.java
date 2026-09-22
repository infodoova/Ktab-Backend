package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PaginationMode;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
public class StructureResolutionTasklet implements Tasklet {

    private final Long bookId;
    private final BookRepository bookRepository;
    private final BookPageRepository pageRepository;
    private final BookSectionRepository sectionRepository;
    private final PaginationModeDetector paginationModeDetector;
    private final TocAligner tocAligner;
    private final HeadingsStructureBuilder headingsStructureBuilder;
    private final SectionTreeBuilder sectionTreeBuilder;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        log.info("StructureResolution START for bookId={}", bookId);
        meterRegistry.counter("ocr.structure.resolution.started").increment();

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        List<BookPage> pages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        if (pages.isEmpty()) {
            log.warn("No pages found for bookId={}, skipping structure resolution", bookId);
            return RepeatStatus.FINISHED;
        }

        // 1. Detect pagination mode
        PaginationMode paginationMode = paginationModeDetector.detect(pages);
        book.setPaginationMode(paginationMode);

        // 2. Clear previous non-manual sections (idempotent rerun)
        sectionRepository.deleteByBook_IdAndSourceNot(bookId, StructureSource.MANUAL);

        // Check if manual sections exist (they take priority)
        List<BookSection> manualSections = sectionRepository.findByBook_IdAndSource(bookId, StructureSource.MANUAL);
        if (!manualSections.isEmpty()) {
            log.info("BookId={} has {} MANUAL sections, preserving as ground truth", bookId, manualSections.size());
            assignPagesToSections(pages, manualSections);
            pageRepository.saveAll(pages);
            return RepeatStatus.FINISHED;
        }

        // 3. Align TOC or fallback to Headings
        List<TocAligner.AlignedSection> alignedSections;
        StructureSource source = book.getStructureSource() != null ? book.getStructureSource() : StructureSource.HEADINGS;

        if (book.getTocRaw() != null && !book.getTocRaw().isBlank()) {
            try {
                RawToc rawToc = objectMapper.readValue(book.getTocRaw(), RawToc.class);
                PageOffsetResolver offsetResolver = new PageOffsetResolver(pages);
                alignedSections = tocAligner.align(rawToc, pages, offsetResolver, paginationMode);
            } catch (Exception e) {
                log.warn("Failed to parse raw TOC, falling back to headings: {}", e.getMessage());
                alignedSections = headingsStructureBuilder.buildFromHeadings(pages);
                source = StructureSource.HEADINGS;
            }
        } else {
            alignedSections = headingsStructureBuilder.buildFromHeadings(pages);
            source = StructureSource.HEADINGS;
        }

        // 4. Build hierarchy & start/end pages
        SectionTreeBuilder.TreeBuildResult treeResult = sectionTreeBuilder.build(
                book,
                alignedSections,
                pages.size(),
                source
        );

        // 5. Persist sections
        List<BookSection> savedSections = sectionRepository.saveAll(treeResult.sections());

        // 6. Assign page section associations
        assignPagesToSections(pages, savedSections);
        pageRepository.saveAll(pages);

        // 7. Update book structure status
        book.setStructureStatus(treeResult.status());
        bookRepository.save(book);

        log.info("StructureResolution DONE for bookId={}, status={}, sectionsCount={}",
                bookId, treeResult.status(), savedSections.size());
        meterRegistry.counter("ocr.structure.resolution.completed", "status", treeResult.status().name()).increment();

        return RepeatStatus.FINISHED;
    }

    private void assignPagesToSections(List<BookPage> pages, List<BookSection> sections) {
        for (BookPage page : pages) {
            int pageNum = page.getPageNumber();
            BookSection deepest = null;

            for (BookSection s : sections) {
                if (s.getStartPage() != null && s.getEndPage() != null) {
                    if (pageNum >= s.getStartPage() && pageNum <= s.getEndPage()) {
                        if (deepest == null || s.getLevel() >= deepest.getLevel()) {
                            deepest = s;
                        }
                    }
                }
            }

            page.setSection(deepest);
        }
    }
}
