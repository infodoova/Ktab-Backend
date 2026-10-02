package com.doova.ktab.service;

import com.doova.ktab.dto.book.ReaderPageResponse;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.model.book.BookSection;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.book.BookSectionRepository;
import com.doova.ktab.service.book.impl.BookTextServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReaderPageServiceTest {

    @Mock
    private BookPageRepository sectionRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookSectionRepository bookSectionRepository;

    @InjectMocks
    private BookTextServiceImpl bookTextService;

    @Test
    @DisplayName("getReaderPage_firstPageRequested_returnsPaginatedReaderPage")
    void getReaderPage_firstPageRequested_returnsPaginatedReaderPage() {
        Long bookId = 1L;
        Book book = new Book();
        book.setTitle("كتاب الاختبار");

        BookPage page = new BookPage();
        page.setPageNumber(1);
        page.setMarkdownContent("هذا نص توضيحي لاختبار القراءة الصفحة الأولى من الكتاب.");

        BookSection section = new BookSection();
        section.setTitle("المقدمة");
        section.setStartPage(1);
        section.setEndPage(5);

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(sectionRepository.getTotalWordCount(bookId)).thenReturn(9);
        when(sectionRepository.streamByBookIdOrderByPageNumberAsc(bookId)).thenReturn(List.of(page).stream());
        when(bookSectionRepository.findByBook_IdOrderBySortOrderAsc(bookId)).thenReturn(List.of(section));

        ReaderPageResponse response = bookTextService.getReaderPage(bookId, 1, 80);

        assertThat(response.bookId()).isEqualTo(bookId);
        assertThat(response.bookTitle()).isEqualTo("كتاب الاختبار");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.wordsPerPage()).isEqualTo(80);
        assertThat(response.wordCount()).isEqualTo(9);
        assertThat(response.content()).isEqualTo("هذا نص توضيحي لاختبار القراءة الصفحة الأولى من الكتاب.");
        assertThat(response.isFirstPage()).isTrue();
        assertThat(response.isLastPage()).isTrue();
        assertThat(response.chapterTitle()).isEqualTo("المقدمة");
    }
}
