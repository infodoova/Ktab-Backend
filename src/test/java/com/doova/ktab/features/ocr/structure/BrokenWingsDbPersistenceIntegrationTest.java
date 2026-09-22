package com.doova.ktab.features.ocr.structure;

import com.doova.ktab.enums.book.PaginationMode;
import com.doova.ktab.enums.book.ReadingDirection;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.features.ocr.config.OcrProperties;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BrokenWingsDbPersistenceIntegrationTest {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookPageRepository pageRepository;

    @Autowired
    private BookSectionRepository sectionRepository;

    @Autowired
    private PaginationModeDetector paginationDetector;

    @Autowired
    private TocAligner tocAligner;

    @Autowired
    private SectionTreeBuilder sectionTreeBuilder;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("End-to-End Database Persistence of 54-page book: الأجنحة المتكسرة")
    @Transactional
    @Rollback
    void testFullPersistenceToDatabase() {
        long tStart = System.currentTimeMillis();

        // Sync PostgreSQL sequences to max existing ID
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('tbl_book_pages', 'col_id'), (SELECT COALESCE(MAX(col_id), 1) FROM tbl_book_pages));");
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('tbl_books', 'col_id'), (SELECT COALESCE(MAX(col_id), 1) FROM tbl_books));");
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('tbl_book_sections', 'col_id'), (SELECT COALESCE(MAX(col_id), 1) FROM tbl_book_sections));");

        // 1. Create and persist Book entity
        Book book = new Book();
        book.setTitle("الأجنحة المتكسرة");
        book.setReadingDirection(ReadingDirection.RTL);
        book = bookRepository.save(book);
        Long bookId = book.getId();
        assertNotNull(bookId);

        // 2. Create and persist 54 pages in tbl_book_pages
        List<BookPage> pages = new ArrayList<>();
        Map<Integer, String> headingsByPage = Map.ofEntries(
                Map.entry(1, "توطئة"),
                Map.entry(3, "الكآبة الخرساء"),
                Map.entry(5, "يد القضاء"),
                Map.entry(8, "في باب الهيكل"),
                Map.entry(10, "الشعلة البيضاء"),
                Map.entry(12, "العاصفة"),
                Map.entry(19, "بحيرة النار"),
                Map.entry(33, "أمام عرش الموت"),
                Map.entry(41, "بين عشتروت والمسيح"),
                Map.entry(44, "التضحية"),
                Map.entry(50, "المنقذ")
        );

        for (int i = 1; i <= 54; i++) {
            BookPage p = new BookPage();
            p.setBook(book);
            p.setPageNumber(i);
            p.setSourcePdfPage(i);
            p.setPrintedPageLabel(String.valueOf(i));

            String heading = headingsByPage.get(i);
            if (heading != null) {
                p.setMarkdownContent("# " + heading + "\n\nنص الصفحة رقم " + i);
                p.setHeadings("[{\"text\": \"" + heading + "\", \"levelHint\": 1}]");
            } else {
                p.setMarkdownContent("نص الصفحة رقم " + i);
            }
            pages.add(p);
        }
        pages = pageRepository.saveAll(pages);
        assertEquals(54, pages.size());

        // 3. Resolve Pagination Mode & Offset
        PaginationMode mode = paginationDetector.detect(pages);
        assertEquals(PaginationMode.PRINTED, mode);
        book.setPaginationMode(mode);

        PageOffsetResolver offsetResolver = new PageOffsetResolver(pages);

        // 4. Parse TOC
        List<RawToc.RawTocEntry> tocEntries = List.of(
                new RawToc.RawTocEntry("توطئة", null, 1, 1, "1", SectionType.CHAPTER),
                new RawToc.RawTocEntry("الكآبة الخرساء", null, 2, 1, "3", SectionType.CHAPTER),
                new RawToc.RawTocEntry("يد القضاء", null, 3, 1, "5", SectionType.CHAPTER),
                new RawToc.RawTocEntry("في باب الهيكل", null, 4, 1, "8", SectionType.CHAPTER),
                new RawToc.RawTocEntry("الشعلة البيضاء", null, 5, 1, "10", SectionType.CHAPTER),
                new RawToc.RawTocEntry("العاصفة", null, 6, 1, "12", SectionType.CHAPTER),
                new RawToc.RawTocEntry("بحيرة النار", null, 7, 1, "19", SectionType.CHAPTER),
                new RawToc.RawTocEntry("أمام عرش الموت", null, 8, 1, "33", SectionType.CHAPTER),
                new RawToc.RawTocEntry("بين عشتروت والمسيح", null, 9, 1, "41", SectionType.CHAPTER),
                new RawToc.RawTocEntry("التضحية", null, 10, 1, "44", SectionType.CHAPTER),
                new RawToc.RawTocEntry("المنقذ", null, 11, 1, "50", SectionType.CHAPTER)
        );
        RawToc rawToc = new RawToc(tocEntries);

        // 5. Align TOC to Pages
        List<TocAligner.AlignedSection> alignedSections = tocAligner.align(rawToc, pages, offsetResolver, mode);
        assertEquals(11, alignedSections.size());

        // 6. Build Section Tree
        SectionTreeBuilder.TreeBuildResult treeResult = sectionTreeBuilder.build(
                book,
                alignedSections,
                54,
                StructureSource.TEXT_LAYER
        );
        assertEquals(11, treeResult.sections().size());
        assertEquals(StructureStatus.RESOLVED, treeResult.status());

        // 7. PERSIST SECTIONS TO tbl_book_sections
        List<BookSection> savedSections = sectionRepository.saveAll(treeResult.sections());
        assertEquals(11, savedSections.size());
        assertNotNull(savedSections.get(0).getId(), "Sections should be assigned generated database IDs");

        // 8. LINK PAGES TO SECTIONS IN tbl_book_pages (col_section_id)
        for (BookPage page : pages) {
            int pageNum = page.getPageNumber();
            BookSection matchingSection = null;
            for (BookSection s : savedSections) {
                if (s.getStartPage() != null && s.getEndPage() != null) {
                    if (pageNum >= s.getStartPage() && pageNum <= s.getEndPage()) {
                        matchingSection = s;
                        break;
                    }
                }
            }
            page.setSection(matchingSection);
        }
        pageRepository.saveAll(pages);

        // 9. UPDATE tbl_books STATUS
        book.setStructureStatus(treeResult.status());
        book.setStructureSource(StructureSource.TEXT_LAYER);
        bookRepository.save(book);

        // 10. VERIFY PERSISTENCE FROM DATABASE
        List<BookSection> dbSections = sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId);
        assertEquals(11, dbSections.size(), "Should find exactly 11 persisted sections in database");

        List<BookPage> dbPages = pageRepository.findByBookIdOrderByPageNumberAsc(bookId);
        assertEquals(54, dbPages.size(), "Should find 54 pages in database");

        // Verify that every page has a foreign key to its respective section in tbl_book_pages
        for (BookPage dbPage : dbPages) {
            assertNotNull(dbPage.getSection(), "Page " + dbPage.getPageNumber() + " must have col_section_id linked in DB");
        }

        long elapsedMs = System.currentTimeMillis() - tStart;
        System.out.printf("%n======================================================%n");
        System.out.printf(">>> [DB PERSISTENCE BENCHMARK] Database Save Completed!%n");
        System.out.printf(">>> Persisted to tbl_books: 1 record (id=%d, status=%s)%n", bookId, book.getStructureStatus());
        System.out.printf(">>> Persisted to tbl_book_pages: %d records with col_section_id linked%n", dbPages.size());
        System.out.printf(">>> Persisted to tbl_book_sections: %d records with hierarchy and ranges%n", dbSections.size());
        System.out.printf(">>> Total Database I/O & Resolution Time: %d ms%n", elapsedMs);
        System.out.printf("======================================================%n%n");
    }
}
