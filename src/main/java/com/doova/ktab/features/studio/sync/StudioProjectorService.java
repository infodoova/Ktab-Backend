package com.doova.ktab.features.studio.sync;

import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.studio.client.dto.StudioChapterDetail;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.enums.StudioChapterOrigin;
import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects ElevenLabs Studio chapter content into the shared content tables
 * ({@code tbl_book_sections}, {@code tbl_book_pages}) using synthetic pagination.
 *
 * <h3>Synthetic pagination (docs/ocr_engine_v3.md, Phase 3.6)</h3>
 * Studio has no page concept. Pages are manufactured by chunking each chapter's canonical
 * text at block boundaries:
 * <ul>
 *   <li>Target ~{@code ktab.studio.page-target-chars} characters per page (default 1 800).</li>
 *   <li>Flush at the first block boundary past the target — never splits mid-block.</li>
 *   <li>An {@code h1}/{@code h2} block always opens a new page.</li>
 *   <li>{@code col_source_pdf_page = NULL}; {@code col_page_kind = BODY};
 *       {@code col_ocr_status = COMPLETED}; {@code col_ocr_model = 'elevenlabs-studio'}.</li>
 * </ul>
 *
 * <h3>Idempotency</h3>
 * Sections are upserted via {@code col_external_chapter_id} (unique index added in Phase 3.3),
 * so re-projecting the same chapter never creates a new section row. Pages are rebuilt on
 * every content change (hash mismatch), which is acceptable because they are always replaced
 * as a unit per chapter — the unique index on
 * {@code (col_book_id, col_external_chapter_id, col_chapter_page_ordinal)} handles conflicts.
 *
 * <h3>Book-wide page numbering</h3>
 * {@code col_page_number} must be globally contiguous. The projector does <em>not</em> assign
 * final page numbers; that is done by a post-projection renumbering pass on the whole book
 * (triggered by the batch step after all chapters are synced). During projection,
 * {@code col_chapter_page_ordinal} is the chapter-local ordinal used by the renumber pass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudioProjectorService {

    private static final String OCR_MODEL_STUDIO = "elevenlabs-studio";
    private static final BigDecimal STUDIO_CONFIDENCE = BigDecimal.ONE;

    private final StudioChapterRepository studioChapterRepository;
    private final BookSectionRepository bookSectionRepository;
    private final BookPageRepository bookPageRepository;
    private final StudioProperties props;

    // =========================================================================
    // Public API — called by StudioSyncService
    // =========================================================================

    /**
     * Project a chapter that is new to our local state (remote-only in the reconciliation).
     * Creates a new {@link BookSection} and a new {@link StudioChapter} row, then builds
     * synthetic pages.
     *
     * @param project    owning project
     * @param detail     full chapter content from the API
     * @param orderIndex chapter's 0-based position in the project's chapter list
     */
    @Transactional
    public void projectNewChapter(StudioProject project, StudioChapterDetail detail, int orderIndex) {
        Book book = project.getBook();

        // Upsert section keyed on external chapter id
        BookSection section = bookSectionRepository
                .findByBook_IdAndExternalChapterId(book.getId(), detail.chapterId())
                .orElseGet(() -> buildSection(book, detail));

        applyChapterToSection(section, detail);
        bookSectionRepository.save(section);

        // Create the StudioChapter link
        StudioChapter studioChapter = new StudioChapter();
        studioChapter.setProject(project);
        studioChapter.setExternalChapterId(detail.chapterId());
        studioChapter.setBookSection(section);
        studioChapter.setOrigin(StudioChapterOrigin.STUDIO_PROJECTED);
        studioChapter.setOrderIndex(orderIndex);
        studioChapterRepository.save(studioChapter);

        // Build synthetic pages
        buildPages(book, section, detail);

        log.debug("studio.projector.new bookId={} extChapterId={} orderIndex={}",
                book.getId(), detail.chapterId(), orderIndex);
    }

    /**
     * Re-project an existing chapter after a content-hash mismatch (Tier 2 content sync).
     * The section row is updated in place (same {@code col_id}); its pages are deleted and
     * rebuilt so the ordinal sequence is always clean.
     *
     * @param studioChapter the local chapter row (already has the old hash)
     * @param detail        fresh chapter content from the API
     */
    @Transactional
    public void reproject(StudioChapter studioChapter, StudioChapterDetail detail) {
        BookSection section = studioChapter.getBookSection();
        Book book = section.getBook();

        applyChapterToSection(section, detail);
        bookSectionRepository.save(section);

        // Delete existing pages for this chapter and rebuild
        bookPageRepository.deleteByBook_IdAndExternalChapterId(book.getId(), detail.chapterId());
        buildPages(book, section, detail);

        log.debug("studio.projector.reproject bookId={} extChapterId={} sectionId={}",
                book.getId(), detail.chapterId(), section.getId());
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private BookSection buildSection(Book book, StudioChapterDetail detail) {
        BookSection section = new BookSection();
        section.setBook(book);
        section.setSectionType(SectionType.CHAPTER);
        section.setSource(StructureSource.STUDIO);
        section.setLevel(1);
        // sortOrder placeholder — the post-projection renumber pass assigns the real value.
        section.setSortOrder(0);
        return section;
    }

    private void applyChapterToSection(BookSection section, StudioChapterDetail detail) {
        String title = extractTitle(detail);
        section.setTitle(title);
        section.setTitleNormalized(title.trim().toLowerCase());
        section.setExternalChapterId(detail.chapterId());
        section.setSource(StructureSource.STUDIO);
        section.setConfidence(STUDIO_CONFIDENCE);
        section.setNeedsReview(false);
    }

    /**
     * Build synthetic pages for a chapter. Pages are split at block boundaries so that
     * no page ever cuts mid-sentence. An {@code h1}/{@code h2} block forces a new page
     * regardless of current accumulation. See Phase 3.6.
     */
    private void buildPages(Book book, BookSection section, StudioChapterDetail detail) {
        if (detail.content() == null || detail.content().blocks() == null) return;

        int targetChars = props.getPageTargetChars();
        List<StudioChapterDetail.Block> blocks = detail.content().blocks();

        List<StudioChapterDetail.Block> currentPage = new ArrayList<>();
        int currentChars = 0;
        int pageOrdinal = 1; // 1-based chapter-local ordinal

        for (StudioChapterDetail.Block block : blocks) {
            boolean isHeading = isTopHeading(block);
            int blockChars = blockCharCount(block);

            // An h1/h2 always opens a new page (flush current first)
            if (isHeading && !currentPage.isEmpty()) {
                savePage(book, section, currentPage, pageOrdinal++, detail.chapterId());
                currentPage = new ArrayList<>();
                currentChars = 0;
            }

            currentPage.add(block);
            currentChars += blockChars;

            // Flush at first block boundary past the target
            if (currentChars >= targetChars) {
                savePage(book, section, currentPage, pageOrdinal++, detail.chapterId());
                currentPage = new ArrayList<>();
                currentChars = 0;
            }
        }

        // Flush remainder
        if (!currentPage.isEmpty()) {
            savePage(book, section, currentPage, pageOrdinal, detail.chapterId());
        }
    }

    private void savePage(
            Book book,
            BookSection section,
            List<StudioChapterDetail.Block> blocks,
            int ordinal,
            String externalChapterId
    ) {
        String markdown = renderMarkdown(blocks);

        BookPage page = bookPageRepository
                .findByBook_IdAndExternalChapterIdAndChapterPageOrdinal(
                        book.getId(), externalChapterId, ordinal)
                .orElseGet(BookPage::new);

        page.setBook(book);
        page.setSection(section);
        page.setMarkdownClean(markdown);
        page.setStatus(OcrStatus.COMPLETED);
        page.setOcrModel(OCR_MODEL_STUDIO);
        page.setExternalChapterId(externalChapterId);
        page.setChapterPageOrdinal(ordinal);
        // col_page_number and col_source_pdf_page are set by the post-projection renumber pass
        page.setPageNumber(0);

        bookPageRepository.save(page);
    }

    /**
     * Convert a list of blocks to Markdown. Heading types map directly; paragraph nodes
     * are concatenated with a blank line between blocks.
     */
    private String renderMarkdown(List<StudioChapterDetail.Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (StudioChapterDetail.Block block : blocks) {
            if (sb.length() > 0) sb.append("\n\n");

            String prefix = switch (block.type() != null ? block.type() : "p") {
                case "h1" -> "# ";
                case "h2" -> "## ";
                case "h3" -> "### ";
                default -> "";
            };
            sb.append(prefix);

            if (block.nodes() != null) {
                for (StudioChapterDetail.Node node : block.nodes()) {
                    if (node.text() != null) sb.append(node.text());
                }
            }
        }
        return sb.toString();
    }

    private String extractTitle(StudioChapterDetail detail) {
        // Prefer the chapter name from the API; fall back to first h1/h2 node text
        if (detail.name() != null && !detail.name().isBlank()) return detail.name();

        if (detail.content() != null && detail.content().blocks() != null) {
            for (StudioChapterDetail.Block block : detail.content().blocks()) {
                if (isTopHeading(block) && block.nodes() != null) {
                    StringBuilder t = new StringBuilder();
                    block.nodes().forEach(n -> { if (n.text() != null) t.append(n.text()); });
                    if (!t.isEmpty()) return t.toString();
                }
            }
        }
        return detail.chapterId(); // last resort
    }

    private static boolean isTopHeading(StudioChapterDetail.Block block) {
        return "h1".equals(block.type()) || "h2".equals(block.type());
    }

    private static int blockCharCount(StudioChapterDetail.Block block) {
        if (block.nodes() == null) return 0;
        return block.nodes().stream()
                .mapToInt(n -> n.text() != null ? n.text().length() : 0)
                .sum();
    }
}

