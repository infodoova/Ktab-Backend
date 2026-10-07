package com.doova.ktab.service.book.impl;

import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.utils.pagination.PageResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookServiceImplCoverImagesTest {

    private final BookRepository bookRepository = mock(BookRepository.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);
    private final FileStorageService fileStorage = mock(FileStorageService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final org.hibernate.Session session = mock(org.hibernate.Session.class, RETURNS_DEEP_STUBS);
    private BookServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = org.mockito.Mockito.mock(BookServiceImpl.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        set("bookRepository", bookRepository);
        set("attachmentService", attachmentService);
        set("fileStorageService", fileStorage);
        set("entityManager", entityManager);
        when(entityManager.unwrap(org.hibernate.Session.class)).thenReturn(session);
        when(fileStorage.getFileUrl(any(), any())).thenAnswer(inv -> "https://signed/" + inv.getArgument(0));
    }

    private void set(String field, Object value) throws Exception {
        var f = BookServiceImpl.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(service, value);
    }

    private static Book book(long id) {
        Book b = new Book();
        b.setId(id);
        return b;
    }

    private static Attachment cover(long bookId, String path) {
        Attachment a = new Attachment();
        a.setEntityId(bookId);
        a.setStoragePath(path);
        return a;
    }

    private void coversExistFor(Map<Long, Attachment> covers) {
        when(attachmentService.getAttachments(anyCollection(), eq(BookResponseBuilderService.BOOK_ENTITY_TYPE),
                eq(BookResponseBuilderService.COVER_IMAGE_TYPE))).thenReturn(covers);
    }

    @Test
    void topReviewedReturnsOnlyCoverUrlsInTheOrderOfTheRanking() {
        Page<Book> ranked = new PageImpl<>(List.of(book(3), book(1), book(2)));
        when(bookRepository.findAllByBookSourceAndTotalReviewsGreaterThan(eq(BookSource.AUTHOR), eq(0), any(Pageable.class)))
                .thenReturn(ranked);
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 2L, cover(2, "c2.jpg"), 3L, cover(3, "c3.jpg")));

        assertThat(service.getTopReviewedCoverImages(10))
                .containsExactly("https://signed/c3.jpg", "https://signed/c1.jpg", "https://signed/c2.jpg");
    }

    @Test
    void topReviewedSkipsBooksWithoutACoverAndStopsAtTheLimit() {
        when(bookRepository.findAllByBookSourceAndTotalReviewsGreaterThan(any(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book(1), book(2), book(3), book(4))));
        coversExistFor(Map.of(2L, cover(2, "c2.jpg"), 3L, cover(3, "c3.jpg"), 4L, cover(4, "c4.jpg")));

        assertThat(service.getTopReviewedCoverImages(2)).containsExactly("https://signed/c2.jpg", "https://signed/c3.jpg");
    }

    @Test
    void topReviewedRanksByRatingThenReviewCountAndLooksAtMoreBooksThanAskedFor() {
        when(bookRepository.findAllByBookSourceAndTotalReviewsGreaterThan(any(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        coversExistFor(Map.of());

        service.getTopReviewedCoverImages(10);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAllByBookSourceAndTotalReviewsGreaterThan(eq(BookSource.AUTHOR), eq(0), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
        assertThat(pageable.getValue().getSort().stream().map(o -> o.getProperty() + " " + o.getDirection()))
                .containsExactly("averageRating DESC", "totalReviews DESC", "id DESC");
    }

    @Test
    void bothListsOnlyEverShowPublishedBooks() {
        when(bookRepository.findAllByBookSourceAndTotalReviewsGreaterThan(any(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(bookRepository.findAllByBookSource(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        coversExistFor(Map.of());

        service.getTopReviewedCoverImages(5);
        service.getCoverImages(0, 5);

        verify(session, org.mockito.Mockito.times(2)).enableFilter("publishedFilter");
    }

    @Test
    void topReviewedIsCappedSoItCannotBeAskedToReturnEverything() {
        when(bookRepository.findAllByBookSourceAndTotalReviewsGreaterThan(any(), anyInt(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        coversExistFor(Map.of());

        service.getTopReviewedCoverImages(100000);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAllByBookSourceAndTotalReviewsGreaterThan(any(), anyInt(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(150);
    }

    @Test
    void allCoverImagesComeBackAsUrlsOnlyWithPagingDetails() {
        when(bookRepository.findAllByBookSource(eq(BookSource.AUTHOR), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book(1), book(2), book(3)), PageRequest.of(0, 50), 3));
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 3L, cover(3, "c3.jpg")));

        PageResponse<String> page = service.getCoverImages(0, 50);

        assertThat(page.getContent()).containsExactly("https://signed/c1.jpg", "https://signed/c3.jpg");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void thePageSizeIsCappedAt200() {
        when(bookRepository.findAllByBookSource(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        coversExistFor(Map.of());

        service.getCoverImages(-3, 100000);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAllByBookSource(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }
}
