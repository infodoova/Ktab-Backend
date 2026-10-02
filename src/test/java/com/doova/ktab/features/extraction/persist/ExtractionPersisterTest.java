package com.doova.ktab.features.extraction.persist;

import com.doova.ktab.enums.book.StructureSource;
import com.doova.ktab.enums.book.StructureStatus;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.features.extraction.BookExtractionService;
import com.doova.ktab.features.extraction.dto.*;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static com.doova.ktab.features.extraction.ExtractionTestSupport.fixture;
import static com.doova.ktab.features.extraction.ExtractionTestSupport.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExtractionPersisterTest {

    private final BookPageRepository pages = mock(BookPageRepository.class);
    private final BookSectionRepository sections = mock(BookSectionRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final ExtractionPersister persister = new ExtractionPersister(pages, sections, books, new ObjectMapper());
    private final BookExtractionService extraction = service();
    private final Book book = new Book();

    @BeforeEach
    void setUp() {
        book.setTitle("كتاب تجريبي");
        when(sections.save(any(BookSection.class))).thenAnswer(i -> i.getArgument(0));
        when(pages.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        when(books.save(any(Book.class))).thenAnswer(i -> i.getArgument(0));
    }

    private List<BookPage> savedPages() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<BookPage>> c = ArgumentCaptor.forClass(Iterable.class);
        verify(pages).saveAll(c.capture());
        List<BookPage> out = new java.util.ArrayList<>();
        c.getValue().forEach(out::add);
        return out;
    }

    private List<BookSection> savedSections() {
        ArgumentCaptor<BookSection> c = ArgumentCaptor.forClass(BookSection.class);
        verify(sections, atLeastOnce()).save(c.capture());
        return c.getAllValues();
    }

    @Test
    void writesOnePageRowPerPdfPageWithRawAndCleanText() {
        persister.persist(book, extraction.extract(fixture("book-outline.pdf")));

        List<BookPage> saved = savedPages();
        assertThat(saved).hasSize(14);
        BookPage p6 = saved.get(5);
        assertThat(p6.getPageNumber()).isEqualTo(6);
        assertThat(p6.getSourcePdfPage()).isEqualTo(6);
        assertThat(p6.getMarkdownContent()).contains("اسم الكتاب");
        assertThat(p6.getMarkdownClean()).doesNotContain("اسم الكتاب").contains("الفصل الأول");
        assertThat(p6.getPrintedPageLabel()).isEqualTo("٣");
        assertThat(p6.getRunningHeader()).isEqualTo("اسم الكتاب");
        assertThat(p6.getStatus()).isEqualTo(OcrStatus.COMPLETED);
        assertThat(p6.getOcrModel()).isEqualTo("text-layer");
        assertThat(p6.getBook()).isSameAs(book);
    }

    @Test
    void deletesWhatWasThereBeforeSoARerunDoesNotDuplicate() {
        book.setId(7L);
        persister.persist(book, extraction.extract(fixture("book-outline.pdf")));

        verify(pages).deleteByBook_Id(7L);
        verify(sections).deleteByBook_Id(7L);
    }

    @Test
    void writesTheSectionTreeWithParentsLevelsRangesAndSource() {
        persister.persist(book, extraction.extract(fixture("book-outline.pdf")));

        List<BookSection> s = savedSections();
        assertThat(s).extracting(BookSection::getTitle, BookSection::getLevel, BookSection::getStartPage, BookSection::getEndPage)
                .containsExactly(
                        tuple("المقدمة", (short) 1, 4, 5), tuple("الفصل الأول", (short) 1, 6, 9),
                        tuple("المبحث الأول", (short) 2, 7, 7), tuple("المبحث الثاني", (short) 2, 8, 9),
                        tuple("الفصل الثاني", (short) 1, 10, 12), tuple("الخاتمة", (short) 1, 13, 14));
        assertThat(s).extracting(BookSection::getSortOrder).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(s.get(2).getParent()).isSameAs(s.get(1));
        assertThat(s.get(0).getParent()).isNull();
        assertThat(s).allSatisfy(x -> assertThat(x.getSource()).isEqualTo(StructureSource.PDF_OUTLINE));
        assertThat(s.get(0).getTitleNormalized()).isNotBlank();
    }

    @Test
    void eachPageBelongsToTheDeepestSectionThatContainsIt() {
        persister.persist(book, extraction.extract(fixture("book-outline.pdf")));

        List<BookPage> p = savedPages();
        assertThat(p.get(6).getSection().getTitle()).isEqualTo("المبحث الأول");  // page 7
        assertThat(p.get(5).getSection().getTitle()).isEqualTo("الفصل الأول");   // page 6
        assertThat(p.get(7).getSection().getTitle()).isEqualTo("المبحث الثاني");  // page 8
        assertThat(p.get(1).getSection()).isNull();                                // front matter
    }

    @Test
    void aSharedStartPageGoesToTheLaterSection() {
        BookExtractionResult real = extraction.extract(fixture("book-outline.pdf"));
        TocEntry a = new TocEntry("الفصل الأول", 1, 6, 6, TocEntryType.CHAPTER, List.of());
        TocEntry b = new TocEntry("الفصل الثاني", 1, 6, 9, TocEntryType.CHAPTER, List.of());
        BookExtractionResult shared = new BookExtractionResult(real.metadata(), real.structureDetection(), List.of(a, b),
                real.chapters(), real.pages(), List.of());

        persister.persist(book, shared);

        assertThat(savedPages().get(5).getSection().getTitle()).isEqualTo("الفصل الثاني");
    }

    @Test
    void imageOnlyAndBlankPagesAreKeptWithTheirKind() {
        persister.persist(book, extraction.extract(fixture("book-hybrid.pdf")));

        List<BookPage> p = savedPages();
        assertThat(p).hasSize(14);
        assertThat(p.get(6).getPageKind()).isEqualTo(com.doova.ktab.enums.book.PageKind.IMAGE_ONLY);
        assertThat(p.get(6).getMarkdownClean()).isEmpty();
        assertThat(p.get(5).getPageKind()).isEqualTo(com.doova.ktab.enums.book.PageKind.BODY);
    }

    @Test
    void bookFieldsAreSetFromTheResult() {
        persister.persist(book, extraction.extract(fixture("book-printed-toc.pdf")));

        assertThat(book.getStructureSource()).isEqualTo(StructureSource.TEXT_LAYER);
        assertThat(book.getStructureStatus()).isEqualTo(StructureStatus.RESOLVED);
        assertThat(book.getPageCount()).isEqualTo(14);
        assertThat(book.getOcrStatus()).isEqualTo(OcrStatus.COMPLETED);
        assertThat(book.getTocRaw()).contains("PRINTED_TOC").contains("الفصل الأول");
    }

    @Test
    void anErrorWarningMeansNeedsReview() {
        BookExtractionResult real = extraction.extract(fixture("book-outline.pdf"));
        BookExtractionResult withError = new BookExtractionResult(real.metadata(), real.structureDetection(), real.toc(),
                real.chapters(), real.pages(), List.of(new ExtractionWarning(WarningCode.LOW_ARABIC_RATIO, Severity.ERROR, "x")));

        persister.persist(book, withError);

        assertThat(book.getStructureStatus()).isEqualTo(StructureStatus.NEEDS_REVIEW);
    }

    @Test
    void noStructureSavesOneSectionForTheWholeBookAndNeedsReview() {
        BookExtractionResult real = extraction.extract(fixture("book-outline.pdf"));
        BookExtractionResult none = new BookExtractionResult(real.metadata(), new StructureDetection(DetectionSource.NONE, 0),
                List.of(), List.of(), real.pages(), List.of());

        persister.persist(book, none);

        List<BookSection> s = savedSections();
        assertThat(s).singleElement().satisfies(x -> {
            assertThat(x.getStartPage()).isEqualTo(1);
            assertThat(x.getEndPage()).isEqualTo(14);
            assertThat(x.isNeedsReview()).isTrue();
        });
        assertThat(book.getStructureStatus()).isEqualTo(StructureStatus.NEEDS_REVIEW);
        assertThat(savedPages()).allSatisfy(pg -> assertThat(pg.getSection()).isSameAs(s.get(0)));
    }
}
