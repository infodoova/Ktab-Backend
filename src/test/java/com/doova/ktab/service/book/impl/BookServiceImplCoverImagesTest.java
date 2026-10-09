package com.doova.ktab.service.book.impl;

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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookServiceImplCoverImagesTest {

    private final BookRepository bookRepository = mock(BookRepository.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);
    private final FileStorageService fileStorage = mock(FileStorageService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final org.hibernate.Session session = mock(org.hibernate.Session.class, RETURNS_DEEP_STUBS);
    private final com.doova.ktab.service.book.BookAboutAudioService aboutAudioService = mock(com.doova.ktab.service.book.BookAboutAudioService.class);
    private BookServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        service = org.mockito.Mockito.mock(BookServiceImpl.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        set("bookRepository", bookRepository);
        set("attachmentService", attachmentService);
        set("fileStorageService", fileStorage);
        set("entityManager", entityManager);
        set("aboutAudioService", aboutAudioService);
        when(entityManager.unwrap(org.hibernate.Session.class)).thenReturn(session);
        when(fileStorage.getFileUrl(any(), any())).thenAnswer(inv -> "https://signed/" + inv.getArgument(0));
        // By default no books are reviewed and the catalog is empty; each test sets up what it needs.
        when(bookRepository.findAllByTotalReviewsGreaterThan(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        coversExistFor(Map.of());
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

    private void reviewedBooks(Book... books) {
        when(bookRepository.findAllByTotalReviewsGreaterThan(eq(0), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(books)));
    }

    // ---------------------------------------------------------------- top reviewed: the reviewed books

    @Test
    void topReviewedReturnsOnlyCoverUrlsInTheOrderOfTheRanking() {
        reviewedBooks(book(3), book(1), book(2));
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 2L, cover(2, "c2.jpg"), 3L, cover(3, "c3.jpg")));

        assertThat(service.getTopReviewedCoverImages(3))
                .containsExactly("https://signed/c3.jpg", "https://signed/c1.jpg", "https://signed/c2.jpg");
    }

    @Test
    void topReviewedSkipsBooksWithoutACoverAndStopsAtTheLimit() {
        reviewedBooks(book(1), book(2), book(3), book(4));
        coversExistFor(Map.of(2L, cover(2, "c2.jpg"), 3L, cover(3, "c3.jpg"), 4L, cover(4, "c4.jpg")));

        assertThat(service.getTopReviewedCoverImages(2)).containsExactly("https://signed/c2.jpg", "https://signed/c3.jpg");
    }

    @Test
    void topReviewedRanksByRatingThenReviewCountAndLooksAtMoreBooksThanAskedFor() {
        service.getTopReviewedCoverImages(10);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAllByTotalReviewsGreaterThan(eq(0), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
        assertThat(pageable.getValue().getSort().stream().map(o -> o.getProperty() + " " + o.getDirection()))
                .containsExactly("averageRating DESC", "totalReviews DESC", "id DESC");
    }

    @Test
    void topReviewedIsCappedSoItCannotBeAskedToReturnEverything() {
        service.getTopReviewedCoverImages(100000);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAllByTotalReviewsGreaterThan(any(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(150);
    }

    @Test
    void bothListsOnlyEverShowPublishedBooks() {
        service.getTopReviewedCoverImages(5);
        service.getCoverImages(0, 5);

        verify(session, times(2)).enableFilter("publishedFilter");
    }

    // ---------------------------------------------------------------- top reviewed: topped up with the newest books

    @Test
    void whenFewBooksHaveReviewsTheListIsToppedUpWithTheNewestBooks() {
        reviewedBooks(book(5));
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(book(9), book(8), book(7))));
        coversExistFor(Map.of(5L, cover(5, "c5.jpg"), 9L, cover(9, "c9.jpg"), 8L, cover(8, "c8.jpg"), 7L, cover(7, "c7.jpg")));

        assertThat(service.getTopReviewedCoverImages(3))
                .containsExactly("https://signed/c5.jpg", "https://signed/c9.jpg", "https://signed/c8.jpg");
    }

    @Test
    void aReviewedBookIsNeverRepeatedInTheTopUp() {
        reviewedBooks(book(5));
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(book(9), book(5), book(8))));
        coversExistFor(Map.of(5L, cover(5, "c5.jpg"), 9L, cover(9, "c9.jpg"), 8L, cover(8, "c8.jpg")));

        assertThat(service.getTopReviewedCoverImages(10))
                .containsExactly("https://signed/c5.jpg", "https://signed/c9.jpg", "https://signed/c8.jpg");
    }

    @Test
    void theTopUpTakesTheNewestPublishedBooksFirst() {
        service.getTopReviewedCoverImages(10);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().stream().map(o -> o.getProperty() + " " + o.getDirection()))
                .containsExactly("publishDate DESC", "id DESC");
    }

    @Test
    void theTopUpSkipsBooksWithoutACover() {
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(book(1), book(2), book(3))));
        coversExistFor(Map.of(3L, cover(3, "c3.jpg")));

        assertThat(service.getTopReviewedCoverImages(2)).containsExactly("https://signed/c3.jpg");
    }

    @Test
    void noTopUpIsNeededWhenTheReviewedBooksAlreadyFillTheList() {
        reviewedBooks(book(1), book(2));
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 2L, cover(2, "c2.jpg")));

        assertThat(service.getTopReviewedCoverImages(2)).hasSize(2);

        verify(bookRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void theTopUpMovesOnToTheNextPageUntilTheListIsFull() {
        Page<Book> firstPage = new PageImpl<>(List.of(book(1), book(2)), PageRequest.of(0, 50), 120);
        Page<Book> secondPage = new PageImpl<>(List.of(book(3)), PageRequest.of(1, 50), 120);
        when(bookRepository.findAll(argThat((Pageable p) -> p != null && p.getPageNumber() == 0))).thenReturn(firstPage);
        when(bookRepository.findAll(argThat((Pageable p) -> p != null && p.getPageNumber() == 1))).thenReturn(secondPage);
        coversExistFor(Map.of(3L, cover(3, "c3.jpg")));

        assertThat(service.getTopReviewedCoverImages(1)).containsExactly("https://signed/c3.jpg");
    }

    @Test
    void theTopUpGivesUpAfterAFewPagesInsteadOfScanningTheWholeCatalog() {
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(book(1)), PageRequest.of(0, 50), 100000));

        assertThat(service.getTopReviewedCoverImages(10)).isEmpty();

        verify(bookRepository, times(4)).findAll(any(Pageable.class));
    }

    @Test
    void anEmptyCatalogGivesAnEmptyList() {
        assertThat(service.getTopReviewedCoverImages(10)).isEmpty();
    }

    // ---------------------------------------------------------------- a fixed list of books

    private static Book published(long id) {
        Book b = book(id);
        b.setStatus(com.doova.ktab.enums.status.BookStatus.PUBLISHED);
        return b;
    }

    private void pinned(Long... ids) throws Exception {
        set("pinnedCoverBookIds", List.of(ids));
    }

    @Test
    void aConfiguredListIsReturnedInItsOwnOrderWhateverTheReviewsSay() throws Exception {
        pinned(110L, 122L, 118L);
        when(bookRepository.findAllById(any())).thenReturn(List.of(published(122), published(118), published(110)));
        coversExistFor(Map.of(110L, cover(110, "c110.jpg"), 122L, cover(122, "c122.jpg"), 118L, cover(118, "c118.jpg")));

        assertThat(service.getTopReviewedCoverImages(10))
                .containsExactly("https://signed/c110.jpg", "https://signed/c122.jpg", "https://signed/c118.jpg");

        verify(bookRepository, never()).findAllByTotalReviewsGreaterThan(any(), any(Pageable.class));
        verify(bookRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void theConfiguredListGivesTheSameBooksEveryTime() throws Exception {
        pinned(3L, 1L, 2L);
        when(bookRepository.findAllById(any())).thenReturn(List.of(published(1), published(2), published(3)));
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 2L, cover(2, "c2.jpg"), 3L, cover(3, "c3.jpg")));

        List<String> first = service.getTopReviewedCoverImages(10);
        List<String> second = service.getTopReviewedCoverImages(10);
        List<String> withDefaultSizeOfSix = service.getTopReviewedCoverImages(6);

        assertThat(first).containsExactly("https://signed/c3.jpg", "https://signed/c1.jpg", "https://signed/c2.jpg");
        assertThat(second).isEqualTo(first);
        assertThat(withDefaultSizeOfSix).isEqualTo(first);
    }

    @Test
    void aConfiguredBookThatIsNotPublishedOrHasNoCoverIsLeftOutNotReplaced() throws Exception {
        pinned(1L, 2L, 3L, 4L);
        // 4 does not exist, 2 is still a draft, 3 has no cover.
        when(bookRepository.findAllById(any())).thenReturn(List.of(published(1), book(2), published(3)));
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 2L, cover(2, "c2.jpg")));

        assertThat(service.getTopReviewedCoverImages(10)).containsExactly("https://signed/c1.jpg");

        verify(bookRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void aSmallerLimitTakesTheFirstOfTheConfiguredBooksAndADuplicateCountsOnce() throws Exception {
        pinned(1L, 2L, 1L, 3L);
        when(bookRepository.findAllById(any())).thenReturn(List.of(published(1), published(2), published(3)));
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 2L, cover(2, "c2.jpg"), 3L, cover(3, "c3.jpg")));

        assertThat(service.getTopReviewedCoverImages(2)).containsExactly("https://signed/c1.jpg", "https://signed/c2.jpg");
        assertThat(service.getTopReviewedCoverImages(10)).containsExactly("https://signed/c1.jpg", "https://signed/c2.jpg", "https://signed/c3.jpg");
    }

    @Test
    void anEmptyConfiguredListMeansPickByReviewsAgain() throws Exception {
        set("pinnedCoverBookIds", List.of());

        service.getTopReviewedCoverImages(10);

        verify(bookRepository).findAllByTotalReviewsGreaterThan(eq(0), any(Pageable.class));
        verify(bookRepository, never()).findAllById(any());
    }

    // ---------------------------------------------------------------- the about-the-book audio comes back with the books

    private static com.doova.ktab.dto.book.BookAboutAudioResponse audio(String url) {
        return com.doova.ktab.dto.book.BookAboutAudioResponse.builder().url(url).description("About " + url).durationSeconds(40).mimeType("audio/mpeg").build();
    }

    @Test
    void theTopReviewedBooksComeWithTheirAudioInTheConfiguredOrder() throws Exception {
        pinned(110L, 122L, 118L);
        Book first = published(110);
        first.setTitle("First");
        when(bookRepository.findAllById(any())).thenReturn(List.of(published(118), first, published(122)));
        coversExistFor(Map.of(110L, cover(110, "c110.jpg"), 122L, cover(122, "c122.jpg"), 118L, cover(118, "c118.jpg")));
        when(aboutAudioService.findAll(any())).thenReturn(Map.of(110L, audio("a110"), 118L, audio("a118")));

        var books = service.getTopReviewedBooks(10);

        assertThat(books).extracting(b -> b.getId()).containsExactly(110L, 122L, 118L);
        assertThat(books).extracting(b -> b.getCoverImageUrl())
                .containsExactly("https://signed/c110.jpg", "https://signed/c122.jpg", "https://signed/c118.jpg");
        assertThat(books.get(0).getTitle()).isEqualTo("First");
        assertThat(books.get(0).getAboutAudio().getUrl()).isEqualTo("a110");
        assertThat(books.get(0).getAboutAudio().getDescription()).isEqualTo("About a110");
        assertThat(books.get(1).getAboutAudio()).isNull();
        assertThat(books.get(2).getAboutAudio().getUrl()).isEqualTo("a118");
        // One query for the audio of all of them, not one per book.
        verify(aboutAudioService).findAll(List.of(110L, 122L, 118L));
    }

    @Test
    void theFullBooksAndTheCoverLinksAreTheSameBooksInTheSameOrder() throws Exception {
        pinned(3L, 1L, 2L);
        when(bookRepository.findAllById(any())).thenReturn(List.of(published(1), published(2), published(3)));
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 2L, cover(2, "c2.jpg"), 3L, cover(3, "c3.jpg")));
        when(aboutAudioService.findAll(any())).thenReturn(Map.of());

        var books = service.getTopReviewedBooks(10);
        var covers = service.getTopReviewedCoverImages(10);

        assertThat(books).extracting(b -> b.getCoverImageUrl()).isEqualTo(covers);
    }

    @Test
    void withoutAFixedListTheFullBooksFollowTheReviewRuleAndKeepTheirAudio() {
        reviewedBooks(book(5));
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(book(9), book(8))));
        coversExistFor(Map.of(5L, cover(5, "c5.jpg"), 9L, cover(9, "c9.jpg"), 8L, cover(8, "c8.jpg")));
        when(aboutAudioService.findAll(any())).thenReturn(Map.of(9L, audio("a9")));

        var books = service.getTopReviewedBooks(3);

        assertThat(books).extracting(b -> b.getId()).containsExactly(5L, 9L, 8L);
        assertThat(books.get(1).getAboutAudio().getUrl()).isEqualTo("a9");
        assertThat(books.get(0).getAboutAudio()).isNull();
    }

    @Test
    void thePublicCatalogPageAlsoCarriesTheAudioOfEachBookInOneQuery() {
        when(bookRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(book(1), book(2)), PageRequest.of(0, 18), 2));
        when(aboutAudioService.findAll(any())).thenReturn(Map.of(2L, audio("a2")));

        var page = service.getBookCovers(0, 18);

        assertThat(page.getContent()).extracting(b -> b.getId()).containsExactly(1L, 2L);
        assertThat(page.getContent().get(0).getAboutAudio()).isNull();
        assertThat(page.getContent().get(1).getAboutAudio().getUrl()).isEqualTo("a2");
        verify(aboutAudioService, times(1)).findAll(List.of(1L, 2L));
    }

    // ---------------------------------------------------------------- all cover images

    @Test
    void allCoverImagesComeBackAsUrlsOnlyWithPagingDetails() {
        when(bookRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(book(1), book(2), book(3)), PageRequest.of(0, 50), 3));
        coversExistFor(Map.of(1L, cover(1, "c1.jpg"), 3L, cover(3, "c3.jpg")));

        PageResponse<String> page = service.getCoverImages(0, 50);

        assertThat(page.getContent()).containsExactly("https://signed/c1.jpg", "https://signed/c3.jpg");
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.isLast()).isTrue();
    }

    @Test
    void thePageSizeIsCappedAt200() {
        service.getCoverImages(-3, 100000);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(bookRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }
}
