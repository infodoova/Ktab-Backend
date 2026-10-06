package com.doova.ktab.features.extraction;

import com.doova.ktab.dto.book.ReaderPageResponse;
import com.doova.ktab.enums.book.PageKind;
import com.doova.ktab.features.extraction.structure.ChapterBuilder;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookExtractionQueryServiceSpacingTest {

    @Test
    void readerPagesPreserveStoredWhitespaceAcrossWordPageBoundary() {
        Long bookId = 10L;
        Book book = new Book();
        book.setId(bookId);
        book.setTitle("Test");

        String storedText = "  Alpha   Beta\n\nGamma\tDelta  ";
        BookPage bookPage = new BookPage();
        bookPage.setPageNumber(1);
        bookPage.setPageKind(PageKind.BODY);
        bookPage.setMarkdownClean(storedText);

        BookRepository books = mock(BookRepository.class);
        BookPageRepository pages = mock(BookPageRepository.class);
        BookSectionRepository sections = mock(BookSectionRepository.class);
        when(books.findById(bookId)).thenReturn(Optional.of(book));
        when(pages.findByBookIdOrderByPageNumberAsc(bookId)).thenReturn(List.of(bookPage));
        when(sections.findByBook_IdOrderBySortOrderAsc(bookId)).thenReturn(List.of());

        BookExtractionQueryService service = new BookExtractionQueryService(
                books, pages, sections, mock(ChapterBuilder.class), new ObjectMapper()
        );

        ReaderPageResponse first = service.getReaderPage(bookId, 1, 2);
        ReaderPageResponse second = service.getReaderPage(bookId, 2, 2);

        assertThat(first.content()).isEqualTo("  Alpha   Beta");
        assertThat(second.content()).isEqualTo("\n\nGamma\tDelta  ");
        assertThat(first.content() + second.content()).isEqualTo(storedText);
    }
}
