package com.doova.ktab.features.studio;

import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layer 3 Contract Test (docs/ocr_engine_v3.md, Phase 3.8).
 *
 * <p>Both the Studio digital ingestion pipeline and the OCR pipeline must write
 * {@code tbl_book_pages}, {@code tbl_book_sections}, and {@code tbl_books} according
 * to the exact same invariant contract:
 * <ul>
 *   <li>{@code col_page_number} contiguous from 1..N, no gaps</li>
 *   <li>{@code col_markdown_clean} non-null on every page</li>
 *   <li>Every page's {@code col_section_id} resolvable (non-null section)</li>
 *   <li>Section {@code col_start_page}/{@code col_end_page} covering the book without gaps or overlaps</li>
 *   <li>{@code tbl_books.col_page_count} equal to the total page count</li>
 * </ul>
 */
class StudioContractTest {

    public static void assertLayer3Contract(Book book, List<BookSection> sections, List<BookPage> pages) {
        assertNotNull(book, "Book must not be null");
        assertNotNull(sections, "Sections list must not be null");
        assertNotNull(pages, "Pages list must not be null");
        assertFalse(pages.isEmpty(), "Book must have at least one page");
        assertFalse(sections.isEmpty(), "Book must have at least one section");

        // 1. col_page_number contiguous from 1, no gaps
        List<BookPage> sortedPages = pages.stream()
                .sorted(Comparator.comparingInt(BookPage::getPageNumber))
                .toList();

        for (int i = 0; i < sortedPages.size(); i++) {
            int expectedPageNumber = i + 1;
            assertEquals(expectedPageNumber, sortedPages.get(i).getPageNumber(),
                    "Page at index " + i + " must have pageNumber=" + expectedPageNumber);
        }

        // 2. col_markdown_clean non-null on every page
        for (BookPage page : sortedPages) {
            assertNotNull(page.getMarkdownClean(),
                    "Page " + page.getPageNumber() + " must have non-null markdownClean");
        }

        // 3. Every page's col_section_id resolvable
        Set<BookSection> validSections = new HashSet<>(sections);
        for (BookPage page : sortedPages) {
            assertNotNull(page.getSection(),
                    "Page " + page.getPageNumber() + " must have an associated section");
            assertTrue(validSections.contains(page.getSection()),
                    "Page " + page.getPageNumber() + " section must belong to book sections");
        }

        // 4. Section col_start_page/col_end_page covering the book without gaps or overlaps
        List<BookSection> sortedSections = sections.stream()
                .sorted(Comparator.comparingInt(BookSection::getStartPage))
                .toList();

        int expectedNextStart = 1;
        for (BookSection section : sortedSections) {
            assertNotNull(section.getStartPage(), "Section startPage must not be null");
            assertNotNull(section.getEndPage(), "Section endPage must not be null");
            assertTrue(section.getStartPage() <= section.getEndPage(),
                    "Section startPage must be <= endPage");
            assertEquals(expectedNextStart, section.getStartPage(),
                    "Section " + section.getTitle() + " must start at page " + expectedNextStart);
            expectedNextStart = section.getEndPage() + 1;
        }

        assertEquals(sortedPages.size() + 1, expectedNextStart,
                "Last section endPage must equal the total page count");

        // 5. tbl_books.col_page_count equal to the page count
        assertNotNull(book.getPageCount(), "Book pageCount must not be null");
        assertEquals(sortedPages.size(), book.getPageCount().intValue(),
                "Book pageCount must equal total pages");
    }

    @Test
    @DisplayName("Layer 3 contract passes for valid book structure")
    void assertLayer3Contract_validBookStructure_passes() {
        Book book = new Book();
        book.setPageCount(4);

        BookSection s1 = new BookSection();
        s1.setTitle("Chapter 1");
        s1.setStartPage(1);
        s1.setEndPage(2);

        BookSection s2 = new BookSection();
        s2.setTitle("Chapter 2");
        s2.setStartPage(3);
        s2.setEndPage(4);

        BookPage p1 = createPage(book, s1, 1, "# Ch 1 Content");
        BookPage p2 = createPage(book, s1, 2, "Ch 1 page 2");
        BookPage p3 = createPage(book, s2, 3, "## Ch 2 Content");
        BookPage p4 = createPage(book, s2, 4, "Ch 2 page 4");

        assertDoesNotThrow(() -> assertLayer3Contract(book, List.of(s1, s2), List.of(p1, p2, p3, p4)));
    }

    @Test
    @DisplayName("Layer 3 contract fails if page numbers have gaps")
    void assertLayer3Contract_pageNumberGap_throwsAssertionError() {
        Book book = new Book();
        book.setPageCount(3);

        BookSection s1 = new BookSection();
        s1.setStartPage(1);
        s1.setEndPage(3);

        BookPage p1 = createPage(book, s1, 1, "Page 1");
        BookPage p2 = createPage(book, s1, 3, "Page 3 (gap at 2)");

        assertThrows(AssertionError.class, () ->
                assertLayer3Contract(book, List.of(s1), List.of(p1, p2)));
    }

    @Test
    @DisplayName("Layer 3 contract fails if markdownClean is null")
    void assertLayer3Contract_nullMarkdown_throwsAssertionError() {
        Book book = new Book();
        book.setPageCount(1);

        BookSection s1 = new BookSection();
        s1.setStartPage(1);
        s1.setEndPage(1);

        BookPage p1 = createPage(book, s1, 1, null);

        assertThrows(AssertionError.class, () ->
                assertLayer3Contract(book, List.of(s1), List.of(p1)));
    }

    @Test
    @DisplayName("Layer 3 contract fails if section pages have overlaps")
    void assertLayer3Contract_sectionOverlap_throwsAssertionError() {
        Book book = new Book();
        book.setPageCount(3);

        BookSection s1 = new BookSection();
        s1.setStartPage(1);
        s1.setEndPage(2);

        BookSection s2 = new BookSection();
        s2.setStartPage(2); // Overlap on page 2!
        s2.setEndPage(3);

        BookPage p1 = createPage(book, s1, 1, "Page 1");
        BookPage p2 = createPage(book, s1, 2, "Page 2");
        BookPage p3 = createPage(book, s2, 3, "Page 3");

        assertThrows(AssertionError.class, () ->
                assertLayer3Contract(book, List.of(s1, s2), List.of(p1, p2, p3)));
    }

    @Test
    @DisplayName("Layer 3 contract fails if book page count mismatches")
    void assertLayer3Contract_mismatchedPageCount_throwsAssertionError() {
        Book book = new Book();
        book.setPageCount(99); // Does not match 2 pages

        BookSection s1 = new BookSection();
        s1.setStartPage(1);
        s1.setEndPage(2);

        BookPage p1 = createPage(book, s1, 1, "Page 1");
        BookPage p2 = createPage(book, s1, 2, "Page 2");

        assertThrows(AssertionError.class, () ->
                assertLayer3Contract(book, List.of(s1), List.of(p1, p2)));
    }

    private static BookPage createPage(Book book, BookSection section, int pageNumber, String markdown) {
        BookPage page = new BookPage();
        page.setBook(book);
        page.setSection(section);
        page.setPageNumber(pageNumber);
        page.setMarkdownClean(markdown);
        return page;
    }
}
