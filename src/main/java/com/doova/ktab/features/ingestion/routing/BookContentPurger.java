package com.doova.ktab.features.ingestion.routing;

import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.features.ocr.service.OcrStorageService;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clears a book's shared content (pages, sections, rendered page images) so a fresh
 * ingestion run starts from a clean slate.
 * <p>
 * Mandatory before either pipeline writes: {@code tbl_book_pages.col_page_number} must stay
 * contiguous 1..n. If a book was previously projected by Studio and the projection gate then
 * fails, rerouting to OCR without purging first produces duplicate or non-contiguous page
 * numbers. This purge is deliberately not something the reroute path has to remember to call —
 * {@link IngestionRouter} calls it unconditionally before every fresh ingest.
 * See docs/ocr_engine_v3.md, Phase 2.2.
 * <p>
 * Does not touch {@code tbl_book_audio_chapters} — durable audio survives a text/structure
 * re-ingestion unless audio is explicitly being regenerated too.
 * <p>
 * Does archive any live {@code tbl_studio_projects} row for the book <strong>before</strong>
 * deleting sections. {@code tbl_studio_chapters} rows cascade-delete via their FK to
 * {@code tbl_book_sections}, but the project row itself does not — and the partial unique
 * index on (bookId, live lifecycle) would otherwise block ever creating a fresh Studio
 * project for this book again. Archiving (not deleting) leaves the remote-deletion decision
 * to the orphan reconciler, which does not need the row to still be "live" to find it.
 * See docs/ocr_engine_v3.md, Phase 3.1 and 4.5.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookContentPurger {

    private final BookRepository bookRepository;
    private final BookPageRepository pageRepository;
    private final BookSectionRepository sectionRepository;
    private final OcrStorageService ocrStorageService;
    private final StudioProjectRepository studioProjectRepository;

    @Transactional
    public void purge(Long bookId) {
        log.info("Purging existing content for bookId={} before fresh ingestion", bookId);

        studioProjectRepository.archiveLiveByBookId(bookId);
        pageRepository.deleteByBook_Id(bookId);
        sectionRepository.deleteByBook_Id(bookId);
        ocrStorageService.deletePages(bookId);

        bookRepository.findById(bookId).ifPresent(this::resetContentFields);
    }

    private void resetContentFields(Book book) {
        book.setPageCount(null);
        book.setStructureStatus(StructureStatus.NONE);
        book.setStructureSource(null);
        book.setTocRaw(null);
        book.setPaginationMode(null);
        bookRepository.save(book);
    }
}
