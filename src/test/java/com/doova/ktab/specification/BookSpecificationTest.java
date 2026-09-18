package com.doova.ktab.specification;

import com.doova.ktab.dto.book.AdvancedBookSearchRequest;
import com.doova.ktab.dto.book.AuthorBookSearchRequest;
import com.doova.ktab.dto.book.LibrarianBookSearchRequest;
import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.enums.status.OcrStatus;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookSpecificationTest {

    @Test
    @DisplayName("forDiscovery_buildsSpecification_notNull")
    void forDiscovery_buildsSpecification_notNull() {
        AdvancedBookSearchRequest request = new AdvancedBookSearchRequest(
                "رواية",
                "ألف ليلة",
                "نجيب محفوظ",
                1L,
                2L,
                List.of(10L, 20L),
                List.of(100L),
                12,
                18,
                BigDecimal.valueOf(4.0),
                BigDecimal.valueOf(5.0),
                2010,
                2024,
                true,
                BookSource.AUTHOR,
                BookStatus.PUBLISHED,
                0,
                10,
                "averageRating",
                null
        );

        Specification<Book> spec = BookSpecification.forDiscovery(request);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("forAuthor_isolatesToAuthorId_notNull")
    void forAuthor_isolatesToAuthorId_notNull() {
        User author = new User();
        author.setId(42L);

        AuthorBookSearchRequest request = new AuthorBookSearchRequest(
                "مسودة",
                BookStatus.DRAFT,
                OcrStatus.COMPLETED,
                false,
                1L,
                2L,
                Instant.now().minusSeconds(86400),
                Instant.now(),
                0,
                10,
                "createdAt",
                null
        );

        Specification<Book> spec = BookSpecification.forAuthor(author, request);
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("forLibrarian_isolatesToOrgId_notNull")
    void forLibrarian_isolatesToOrgId_notNull() {
        LibrarianBookSearchRequest request = new LibrarianBookSearchRequest(
                "مخطوطة",
                "ابن خلدون",
                "ar",
                1L,
                null,
                false,
                OcrStatus.COMPLETED,
                BookStatus.PUBLISHED,
                0,
                10,
                "createdAt",
                null
        );

        Specification<Book> spec = BookSpecification.forLibrarian(99L, request);
        assertThat(spec).isNotNull();
    }
}
