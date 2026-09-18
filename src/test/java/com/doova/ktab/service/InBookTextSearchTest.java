package com.doova.ktab.service;

import com.doova.ktab.dto.book.InBookTextSearchResponse;
import com.doova.ktab.model.book.BookPage;
import com.doova.ktab.repository.book.BookPageRepository;
import com.doova.ktab.service.book.impl.BookTextServiceImpl;
import com.doova.ktab.utils.pagination.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InBookTextSearchTest {

    @Mock
    private BookPageRepository sectionRepository;

    @InjectMocks
    private BookTextServiceImpl bookTextService;

    @Test
    @DisplayName("searchInBook_validKeyword_returnsSnippetsWithHighlight")
    void searchInBook_validKeyword_returnsSnippetsWithHighlight() {
        Long bookId = 1L;
        String keyword = "العدالة";

        BookPage page1 = new BookPage();
        page1.setPageNumber(15);
        page1.setMarkdownContent("وفي هذا الفصل نتطرق إلى مفهوم العدالة الاجتماعية وأثرها على المجتمع.");
        page1.setWordCount(12);

        Pageable pageable = PageRequest.of(0, 10);
        when(sectionRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(page1), pageable, 1));

        PageResponse<InBookTextSearchResponse> response = bookTextService.searchInBook(bookId, keyword, pageable);

        assertThat(response.getContent()).hasSize(1);
        InBookTextSearchResponse item = response.getContent().get(0);
        assertThat(item.pageNumber()).isEqualTo(15);
        assertThat(item.snippet()).contains("[[HIGHLIGHT]]العدالة[[/HIGHLIGHT]]");
        assertThat(item.matchCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("searchInBook_emptyKeyword_returnsEmptyPage")
    void searchInBook_emptyKeyword_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        PageResponse<InBookTextSearchResponse> response = bookTextService.searchInBook(1L, "  ", pageable);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }
}
