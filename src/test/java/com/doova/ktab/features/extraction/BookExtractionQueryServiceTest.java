package com.doova.ktab.features.extraction;

import com.doova.ktab.dto.book.ReaderPageResponse;
import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.enums.book.SectionType;
import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.features.extraction.structure.ChapterBuilder;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookExtractionQueryServiceTest {

    private final BookRepository bookRepository = mock(BookRepository.class);
    private final BookPageRepository pageRepository = mock(BookPageRepository.class);
    private final BookSectionRepository sectionRepository = mock(BookSectionRepository.class);
    private final ChapterBuilder chapterBuilder = mock(ChapterBuilder.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private BookExtractionQueryService service;

    @BeforeEach
    void setUp() {
        service = new BookExtractionQueryService(
                bookRepository,
                pageRepository,
                sectionRepository,
                chapterBuilder,
                objectMapper
        );
    }

    @Test
    void getReaderPage_skipsIntroductionsBibliographiesAndAppendixes_andPaginatesContinuously() {
        Long bookId = 100L;
        Book book = new Book();
        book.setId(bookId);
        book.setTitle("كتاب الفلسفة والحكمة");

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));

        // Create Sections:
        // 1. Introduction (pages 1-3) -> EXCLUDED
        BookSection introSection = new BookSection();
        introSection.setId(1L);
        introSection.setTitle("مقدمة المحقق");
        introSection.setSectionType(SectionType.INTRODUCTION);
        introSection.setSortOrder(1);
        introSection.setStartPage(1);
        introSection.setEndPage(3);

        // 2. Chapter 1 (pages 4-6) -> BODY (BOOK STARTER)
        BookSection ch1Section = new BookSection();
        ch1Section.setId(2L);
        ch1Section.setTitle("الفصل الأول: البدايات الأولى");
        ch1Section.setSectionType(SectionType.CHAPTER);
        ch1Section.setSortOrder(2);
        ch1Section.setStartPage(4);
        ch1Section.setEndPage(6);

        // 3. Bibliography (pages 7-8) -> EXCLUDED
        BookSection biblioSection = new BookSection();
        biblioSection.setId(3L);
        biblioSection.setTitle("المصادر والمراجع");
        biblioSection.setSectionType(SectionType.BIBLIOGRAPHY);
        biblioSection.setSortOrder(3);
        biblioSection.setStartPage(7);
        biblioSection.setEndPage(8);

        // 4. Appendix (pages 9-10) -> EXCLUDED
        BookSection appendixSection = new BookSection();
        appendixSection.setId(4L);
        appendixSection.setTitle("الملحق التوثيقي");
        appendixSection.setSectionType(SectionType.APPENDIX);
        appendixSection.setSortOrder(4);
        appendixSection.setStartPage(9);
        appendixSection.setEndPage(10);
        appendixSection.setBook(book);

        List<BookSection> sections = List.of(introSection, ch1Section, biblioSection, appendixSection);
        when(sectionRepository.findByBook_IdOrderBySortOrderAsc(bookId)).thenReturn(sections);

        // Create Pages:
        List<BookPage> pages = new ArrayList<>();

        // Page 1: Introduction text
        BookPage p1 = new BookPage();
        p1.setId(101L);
        p1.setPageNumber(1);
        p1.setPageKind(PageKind.BODY);
        p1.setSection(introSection);
        p1.setMarkdownClean("هذه مقدمة لا ينبغي أن تظهر في نص البداية إطلاقا");
        pages.add(p1);

        // Page 4: Chapter 1 start (100 words)
        StringBuilder ch1P4Words = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            ch1P4Words.append("كلمة").append(i).append(" ");
        }
        BookPage p4 = new BookPage();
        p4.setId(104L);
        p4.setPageNumber(4);
        p4.setPageKind(PageKind.BODY);
        p4.setSection(ch1Section);
        p4.setMarkdownClean(ch1P4Words.toString().trim());
        pages.add(p4);

        // Page 5: Chapter 1 continuation (100 words)
        StringBuilder ch1P5Words = new StringBuilder();
        for (int i = 101; i <= 200; i++) {
            ch1P5Words.append("كلمة").append(i).append(" ");
        }
        BookPage p5 = new BookPage();
        p5.setId(105L);
        p5.setPageNumber(5);
        p5.setPageKind(PageKind.BODY);
        p5.setSection(ch1Section);
        p5.setMarkdownClean(ch1P5Words.toString().trim());
        pages.add(p5);

        // Page 7: Bibliography
        BookPage p7 = new BookPage();
        p7.setId(107L);
        p7.setPageNumber(7);
        p7.setPageKind(PageKind.BODY);
        p7.setSection(biblioSection);
        p7.setMarkdownClean("قائمة المراجع والمصادر التوثيقية الممنوعة من العرض");
        pages.add(p7);

        // Page 9: Appendix
        BookPage p9 = new BookPage();
        p9.setId(109L);
        p9.setPageNumber(9);
        p9.setPageKind(PageKind.BODY);
        p9.setSection(appendixSection);
        p9.setMarkdownClean("ملحق الوثائق والبيانات الإحصائية الممنوعة من العرض");
        pages.add(p9);

        when(pageRepository.findByBookIdOrderByPageNumberAsc(bookId)).thenReturn(pages);

        // Total words in book before appendixes: 210 words (10 from p1 introduction + 100 from p4 + 100 from p5)
        // With wordsPerPage = 80:
        // Page 1: words 0..79 (80 words)
        // Page 2: words 80..159 (80 words)
        // Page 3: words 160..209 (50 words)

        // Test Page 1:
        ReaderPageResponse page1 = service.getReaderPage(bookId, 1, 80);
        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.totalPages()).isEqualTo(3);
        assertThat(page1.wordsPerPage()).isEqualTo(80);
        assertThat(page1.wordCount()).isEqualTo(80);
        assertThat(page1.startWordIndex()).isEqualTo(0L);
        assertThat(page1.endWordIndex()).isEqualTo(80L);
        assertThat(page1.totalWords()).isEqualTo(210L);
        assertThat(page1.isFirstPage()).isTrue();
        assertThat(page1.isLastPage()).isFalse();
        assertThat(page1.hasNextPage()).isTrue();
        // Starts with introduction words and continues continuously without assumptions
        assertThat(page1.content()).startsWith("هذه مقدمة");
        assertThat(page1.content()).doesNotContain("المراجع");
        assertThat(page1.content()).doesNotContain("ملحق");

        // Test Page 2:
        ReaderPageResponse page2 = service.getReaderPage(bookId, 2, 80);
        assertThat(page2.page()).isEqualTo(2);
        assertThat(page2.wordCount()).isEqualTo(80);
        assertThat(page2.startWordIndex()).isEqualTo(80L);
        assertThat(page2.endWordIndex()).isEqualTo(160L);
        assertThat(page2.isFirstPage()).isFalse();
        assertThat(page2.isLastPage()).isFalse();
        assertThat(page2.hasNextPage()).isTrue();

        // Test Page 3 (final slice):
        ReaderPageResponse page3 = service.getReaderPage(bookId, 3, 80);
        assertThat(page3.page()).isEqualTo(3);
        assertThat(page3.wordCount()).isEqualTo(50);
        assertThat(page3.startWordIndex()).isEqualTo(160L);
        assertThat(page3.endWordIndex()).isEqualTo(210L);
        assertThat(page3.isLastPage()).isTrue();
        assertThat(page3.hasNextPage()).isFalse();
        assertThat(page3.content()).doesNotContain("المراجع");
        assertThat(page3.content()).doesNotContain("ملحق");

        // Test Navigator API:
        com.doova.ktab.dto.book.BookNavigatorResponse navigator = service.getNavigator(bookId, 80);
        assertThat(navigator.bookId()).isEqualTo(bookId);
        assertThat(navigator.sections()).hasSize(2); // Intro and Chapter 1
        assertThat(navigator.sections().get(0).title()).isEqualTo("مقدمة المحقق");
        assertThat(navigator.sections().get(0).readerPage()).isEqualTo(1);
    }
}
