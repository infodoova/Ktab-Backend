package com.doova.ktab.service.book.impl;

import com.doova.ktab.dto.book.BookRequestDto;
import com.doova.ktab.dto.book.BookSearchRequestDto;
import com.doova.ktab.dto.book.BookAboutAudioResponse;
import com.doova.ktab.dto.book.BookCoverResponse;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.mappers.book.BookMapper;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.BookAboutAudioService;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.service.book.BookStatusTransition;
import com.doova.ktab.service.book.BookFileService;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.utils.pagination.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.time.Instant;
import com.doova.ktab.dto.book.BookSourceFileResponseDto;
import com.doova.ktab.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final BookFileService bookFileService;
    private final EntityManager entityManager;
    private final BookResponseBuilderService responseBuilder;
    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;
    private final BookAboutAudioService aboutAudioService;

    /**
     * Books the top reviewed covers always show, in this order (ktab.public.top-reviewed-cover-book-ids, comma separated).
     * Empty means no fixed list: the covers are then picked by reviews.
     */
    @Value("${ktab.public.top-reviewed-cover-book-ids:}")
    private List<Long> pinnedCoverBookIds = List.of();

    // =========================================================
    // FILTER
    // =========================================================
    private void enablePublishedFilter() {
        entityManager.unwrap(Session.class).enableFilter("publishedFilter").setParameter("status", BookStatus.PUBLISHED.name());
    }

    // =========================================================
    // CREATE
    // =========================================================
    @Override
    @Transactional
    public BookResponseDto createBook(BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author) {
        assertStatusAssignable(dto);

        Book book = bookMapper.toEntity(dto);
        book.setAuthor(author);

        Book savedBook = bookRepository.save(book);
        // Files must be handled before we try to get the pdfKey
        bookFileService.handleCreateFiles(savedBook, cover, pdf);

        return responseBuilder.build(savedBook, true);
    }

    // =========================================================
    // UPDATE
    // =========================================================
    @Override
    @Transactional
    public BookResponseDto updateBook(Long id, BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author) {
        assertStatusAssignable(dto);

        Book book = bookRepository.findByIdAndAuthor(id, author)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_OWNER));

        assertEditable(book);

        bookMapper.updateBookFromDto(dto, book);
        bookFileService.handleUpdateFiles(book, cover, pdf);

        Book savedBook = bookRepository.save(book);

        return responseBuilder.build(savedBook, true);
    }

    // =========================================================
    // GET FOR AUTHOR
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public BookResponseDto getBookByIdForAuthor(Long id, User author) {
        Book book = bookRepository.findByIdAndAuthor(id, author).orElseThrow(() -> new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_FOUND));
        return responseBuilder.build(book, true);
    }

    // =========================================================
    // GET BY AUTHOR
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getBooksByAuthorId(User author, int page, int size, String status) {
        return getBooksByAuthorId(author.getId(), page, size, status);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getBooksByAuthorId(Long authorId, int page, int size, String status) {
        var pageable = PageRequest.of(page, size, Sort.by("id").descending());

        org.springframework.data.domain.Page<Book> pageResult;
        if (StringUtils.hasText(status)) {
            BookStatus bookStatus;
            try {
                bookStatus = BookStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(ApiMessageKey.BOOK_INVALID_STATUS);
            }
            pageResult = bookRepository.findAllByAuthorIdAndStatusAndBookSource(authorId, bookStatus, BookSource.AUTHOR, pageable);
        } else {
            pageResult = bookRepository.findAllByAuthorIdAndBookSource(authorId, BookSource.AUTHOR, pageable);
        }

        return mapBookPage(pageResult);
    }

    // GET BOOK BY ID (READER)
    @Override
    @Transactional(readOnly = true)
    public BookResponseDto getBookById(Long id) {
        enablePublishedFilter();

        Book book = bookRepository.findById(id).orElseThrow(() -> new BadRequestException(ApiMessageKey.READER_BOOK_NOT_FOUND));

        return responseBuilder.build(book);
    }

    // =========================================================
    // DELETE
    // =========================================================
    @Override
    @Transactional
    public void deleteBook(Long id, User author) {
        Book book = bookRepository.findByIdAndAuthor(id, author).orElseThrow(() -> new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_OWNER));

        assertEditable(book);

        bookFileService.handleDeleteFiles(book);
        bookRepository.delete(book);
    }

    // =========================================================
    // SUBMIT / WITHDRAW (AUTHOR REVIEW WORKFLOW)
    // =========================================================
    @Override
    @Transactional
    public BookResponseDto submitForReview(Long id, User author) {
        Book book = bookRepository.findByIdAndAuthor(id, author)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_OWNER));

        BookStatusTransition.assertAllowed(book.getStatus(), BookStatus.UNDER_REVIEW);

        book.setStatus(BookStatus.UNDER_REVIEW);
        book.setSubmittedAt(Instant.now());
        book.setReviewNote(null);

        Book savedBook = bookRepository.save(book);
        return responseBuilder.build(savedBook, true);
    }

    @Override
    @Transactional
    public BookResponseDto createAndSubmit(BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author) {

        Book book = bookMapper.toEntity(dto);
        book.setAuthor(author);
        // Force DRAFT first so the status-transition guard can validate DRAFT → UNDER_REVIEW
        book.setStatus(BookStatus.DRAFT);

        Book savedBook = bookRepository.save(book);
        bookFileService.handleCreateFiles(savedBook, cover, pdf);

        // Transition DRAFT → UNDER_REVIEW through the same guard used by standalone submit
        BookStatusTransition.assertAllowed(savedBook.getStatus(), BookStatus.UNDER_REVIEW);
        savedBook.setStatus(BookStatus.UNDER_REVIEW);
        savedBook.setSubmittedAt(Instant.now());
        savedBook.setReviewNote(null);

        savedBook = bookRepository.save(savedBook);
        return responseBuilder.build(savedBook, true);
    }

    @Override
    @Transactional
    public BookResponseDto withdrawFromReview(Long id, User author) {
        Book book = bookRepository.findByIdAndAuthor(id, author)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_OWNER));

        BookStatusTransition.assertAllowed(book.getStatus(), BookStatus.DRAFT);

        book.setStatus(BookStatus.DRAFT);

        Book savedBook = bookRepository.save(book);
        return responseBuilder.build(savedBook, true);
    }

    // =========================================================
    // READER – GET ALL PUBLISHED BOOKS (PAGINATED)
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getAllBooksPaginated(int page, int size) {
        enablePublishedFilter();

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishDate"));

        var booksPage = bookRepository.findAllByStatus(BookStatus.PUBLISHED, pageable);

        return new PageResponse<>(booksPage.getContent().stream().map(responseBuilder::build).toList(), booksPage.getNumber(), booksPage.getSize(), booksPage.getTotalElements(), booksPage.getTotalPages(), booksPage.isLast());
    }

    // =========================================================
    // READER – SEARCH BOOKS (CRITERIA QUERY)
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> searchBooks(BookSearchRequestDto req, Pageable pageable) {
        enablePublishedFilter();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        // =====================================================
        // MAIN QUERY
        // =====================================================
        CriteriaQuery<Book> query = cb.createQuery(Book.class);
        Root<Book> root = query.from(Book.class);

        List<Predicate> predicates = buildPredicates(req, cb, root);

        query.where(predicates.toArray(Predicate[]::new)).orderBy(cb.desc(root.get("averageRating")));

        TypedQuery<Book> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Book> books = typedQuery.getResultList();

        // =====================================================
        // COUNT QUERY (SEPARATE ROOT + SEPARATE PREDICATES)
        // =====================================================
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<Book> countRoot = countQuery.from(Book.class);

        List<Predicate> countPredicates = buildPredicates(req, cb, countRoot);

        countQuery.select(cb.count(countRoot)).where(countPredicates.toArray(Predicate[]::new));

        long totalElements = entityManager.createQuery(countQuery).getSingleResult();

        int totalPages = (int) Math.ceil((double) totalElements / pageable.getPageSize());
        boolean isLast = pageable.getPageNumber() + 1 >= totalPages;

        return new PageResponse<>(books.stream().map(responseBuilder::build).toList(), pageable.getPageNumber(), pageable.getPageSize(), totalElements, totalPages, isLast);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> advancedSearchBooks(com.doova.ktab.dto.book.AdvancedBookSearchRequest req, Pageable pageable) {
        enablePublishedFilter();
        org.springframework.data.domain.Page<Book> booksPage = bookRepository.findAll(
                com.doova.ktab.specification.BookSpecification.forDiscovery(req),
                pageable
        );
        return new PageResponse<>(
                booksPage.getContent().stream().map(responseBuilder::build).toList(),
                booksPage.getNumber(),
                booksPage.getSize(),
                booksPage.getTotalElements(),
                booksPage.getTotalPages(),
                booksPage.isLast()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> searchAuthorBooks(User author, com.doova.ktab.dto.book.AuthorBookSearchRequest req, Pageable pageable) {
        org.springframework.data.domain.Page<Book> booksPage = bookRepository.findAll(
                com.doova.ktab.specification.BookSpecification.forAuthor(author, req),
                pageable
        );
        return new PageResponse<>(
                booksPage.getContent().stream().map(responseBuilder::build).toList(),
                booksPage.getNumber(),
                booksPage.getSize(),
                booksPage.getTotalElements(),
                booksPage.getTotalPages(),
                booksPage.isLast()
        );
    }

    private void assertStatusAssignable(BookRequestDto dto) {
        if (dto.getStatus() != null && dto.getStatus() != BookStatus.DRAFT) {
            throw new BadRequestException(ApiMessageKey.BOOK_STATUS_NOT_ASSIGNABLE);
        }
    }

    private void assertEditable(Book book) {
        if (book.getStatus() == BookStatus.PUBLISHED) {
            throw new BadRequestException(ApiMessageKey.AUTHOR_BOOK_UPDATE_FORBIDDEN);
        }
        if (book.getStatus() == BookStatus.UNDER_REVIEW) {
            throw new BadRequestException(ApiMessageKey.AUTHOR_BOOK_UPDATE_UNDER_REVIEW);
        }
    }

    private List<Predicate> buildPredicates(BookSearchRequestDto req, CriteriaBuilder cb, Root<Book> root) {
        List<Predicate> predicates = new ArrayList<>();

        // TITLE
        if (StringUtils.hasText(req.title())) {
            predicates.add(cb.like(cb.lower(root.get("title")), "%" + req.title().toLowerCase() + "%"));
        }

        // MAIN GENRE
        if (req.mainGenreIds() != null && !req.mainGenreIds().isEmpty()) {
            predicates.add(root.get("mainGenre").get("id").in(req.mainGenreIds()));
        }

        // SUB GENRE
        if (req.subGenreIds() != null && !req.subGenreIds().isEmpty()) {
            predicates.add(root.get("subGenre").get("id").in(req.subGenreIds()));
        }

        // AGE RANGE
        if (req.age() != null) {
            predicates.add(cb.le(root.get("ageRangeMin"), req.age()));
            predicates.add(cb.ge(root.get("ageRangeMax"), req.age()));
        }

        // MIN AVERAGE RATING
        if (req.minAverageRating() != null) {
            predicates.add(cb.ge(root.get("averageRating"), req.minAverageRating()));
        }

        // BOOK SOURCE: Filter by bookSource only if explicitly specified (returns both AUTHOR and LIBRARY by default)
        if (req.bookSource() != null) {
            predicates.add(cb.equal(root.get("bookSource"), req.bookSource()));
        }

        return predicates;
    }

    // =========================================================
    // PUBLIC – GET BOOK COVERS WITH TITLES (NO AUTH REQUIRED)
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookCoverResponse> getBookCovers(int page, int size) {
        enablePublishedFilter();

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishDate"));
        var booksPage = bookRepository.findAll(pageable);

        Map<Long, BookAboutAudioResponse> audios = aboutAudioService.findAll(booksPage.getContent().stream().map(Book::getId).toList());
        List<BookCoverResponse> covers = booksPage.getContent().stream()
                .map(book -> {
                    String coverUrl = attachmentService
                            .getAttachment(book.getId(), BookResponseBuilderService.BOOK_ENTITY_TYPE, BookResponseBuilderService.COVER_IMAGE_TYPE)
                            .map(attachment -> fileStorageService.getFileUrl(attachment.getStoragePath(), UrlStrategy.SIGNED))
                            .orElse(null);

                    return toCoverResponse(book, coverUrl, audios.get(book.getId()));
                })
                .toList();

        return new PageResponse<>(
                covers,
                booksPage.getNumber(),
                booksPage.getSize(),
                booksPage.getTotalElements(),
                booksPage.getTotalPages(),
                booksPage.isLast()
        );
    }

    // =========================================================
    // PUBLIC COVER IMAGES (cover URL only)
    // =========================================================
    private static final int TOP_REVIEWED_MAX = 50;
    private static final int COVER_IMAGES_MAX_PAGE_SIZE = 200;
    /** When topping up with the newest books: how many books per query, and how many queries at most. */
    private static final int TOP_UP_PAGE_SIZE = 50;
    private static final int TOP_UP_MAX_PAGES = 4;

    /** A selected book together with the signed link to its cover image. */
    private record BookWithCover(Book book, String coverUrl) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getTopReviewedCoverImages(int limit) {
        return selectTopReviewed(limit).stream().map(BookWithCover::coverUrl).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookCoverResponse> getTopReviewedBooks(int limit) {
        List<BookWithCover> selected = selectTopReviewed(limit);
        Map<Long, BookAboutAudioResponse> audios = aboutAudioService.findAll(selected.stream().map(s -> s.book().getId()).toList());
        return selected.stream()
                .map(s -> toCoverResponse(s.book(), s.coverUrl(), audios.get(s.book().getId())))
                .toList();
    }

    /**
     * The books shown as the top reviewed: the configured fixed list when there is one, otherwise the reviewed books first
     * (best rated first) topped up with the newest published books. Books without a cover image are left out.
     */
    private List<BookWithCover> selectTopReviewed(int limit) {
        enablePublishedFilter();

        int wanted = Math.min(Math.max(limit, 1), TOP_REVIEWED_MAX);
        if (pinnedCoverBookIds != null && !pinnedCoverBookIds.isEmpty()) {
            return pinnedBooks(wanted);
        }

        Sort bestFirst = Sort.by(Sort.Direction.DESC, "averageRating")
                .and(Sort.by(Sort.Direction.DESC, "totalReviews"))
                .and(Sort.by(Sort.Direction.DESC, "id"));
        // A book with no cover image is skipped, so look at more books than asked for.
        List<Book> candidates = bookRepository
                .findAllByTotalReviewsGreaterThan(0, PageRequest.of(0, wanted * 3, bestFirst))
                .getContent();

        Map<Long, String> coverUrls = coverImageUrls(candidates);
        List<BookWithCover> selected = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Book book : candidates) {
            seen.add(book.getId());
            String url = coverUrls.get(book.getId());
            if (url != null && selected.size() < wanted) {
                selected.add(new BookWithCover(book, url));
            }
        }

        if (selected.size() < wanted) {
            addNewestBooks(selected, seen, wanted);
        }
        return selected;
    }

    /**
     * The configured books, in the configured order, whatever the reviews say. A configured book that is not published,
     * does not exist or has no cover image is left out (and logged) instead of being replaced by another one.
     */
    private List<BookWithCover> pinnedBooks(int wanted) {
        List<Long> ids = pinnedCoverBookIds.stream().distinct().toList();
        Map<Long, Book> published = new HashMap<>();
        for (Book book : bookRepository.findAllById(ids)) {
            if (book.getStatus() == BookStatus.PUBLISHED) {
                published.put(book.getId(), book);
            }
        }
        Map<Long, String> coverUrls = coverImageUrls(new ArrayList<>(published.values()));

        List<BookWithCover> selected = new ArrayList<>();
        for (Long id : ids) {
            String url = published.containsKey(id) ? coverUrls.get(id) : null;
            if (url == null) {
                log.warn("Pinned top reviewed cover: book {} is not published or has no cover image, skipped", id);
            } else if (selected.size() < wanted) {
                selected.add(new BookWithCover(published.get(id), url));
            }
        }
        return selected;
    }

    /**
     * Tops the list up with the newest published books, so a catalog with few reviewed books still gets a full list.
     * Books already in the list, and books without a cover image, are skipped.
     */
    private void addNewestBooks(List<BookWithCover> selected, Set<Long> seen, int wanted) {
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "publishDate").and(Sort.by(Sort.Direction.DESC, "id"));

        for (int page = 0; page < TOP_UP_MAX_PAGES && selected.size() < wanted; page++) {
            var booksPage = bookRepository.findAll(PageRequest.of(page, TOP_UP_PAGE_SIZE, newestFirst));
            List<Book> books = booksPage.getContent().stream().filter(book -> !seen.contains(book.getId())).toList();

            Map<Long, String> coverUrls = coverImageUrls(books);
            for (Book book : books) {
                String url = coverUrls.get(book.getId());
                if (url != null && selected.size() < wanted) {
                    selected.add(new BookWithCover(book, url));
                    seen.add(book.getId());
                }
            }
            if (booksPage.isLast()) {
                break;
            }
        }
    }

    private BookCoverResponse toCoverResponse(Book book, String coverUrl, BookAboutAudioResponse aboutAudio) {
        return BookCoverResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .coverImageUrl(coverUrl)
                .description(book.getDescription())
                .language(book.getLanguage())
                .ageRangeMin(book.getAgeRangeMin())
                .ageRangeMax(book.getAgeRangeMax())
                .pageCount(book.getPageCount())
                .publishDate(book.getPublishDate())
                .mainGenre(book.getMainGenre() != null ? book.getMainGenre().getNameAr() : null)
                .subGenre(book.getSubGenre() != null ? book.getSubGenre().getNameAr() : null)
                .aboutAudio(aboutAudio)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<String> getCoverImages(int page, int size) {
        enablePublishedFilter();

        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), COVER_IMAGES_MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "publishDate").and(Sort.by(Sort.Direction.DESC, "id")));
        var booksPage = bookRepository.findAll(pageable);

        Map<Long, String> coverUrls = coverImageUrls(booksPage.getContent());
        List<String> urls = booksPage.getContent().stream()
                .map(book -> coverUrls.get(book.getId()))
                .filter(Objects::nonNull)
                .toList();

        return new PageResponse<>(
                urls,
                booksPage.getNumber(),
                booksPage.getSize(),
                booksPage.getTotalElements(),
                booksPage.getTotalPages(),
                booksPage.isLast()
        );
    }

    /** Signed cover image URLs for the books that have one, found with a single query for all of them. */
    private Map<Long, String> coverImageUrls(List<Book> books) {
        List<Long> ids = books.stream().map(Book::getId).toList();
        Map<Long, String> urls = new HashMap<>();
        attachmentService.getAttachments(ids, BookResponseBuilderService.BOOK_ENTITY_TYPE, BookResponseBuilderService.COVER_IMAGE_TYPE)
                .forEach((bookId, attachment) ->
                        urls.put(bookId, fileStorageService.getFileUrl(attachment.getStoragePath(), UrlStrategy.SIGNED)));
        return urls;
    }

    // =========================================================
    // HELPER
    // =========================================================
    private PageResponse<BookResponseDto> mapBookPage(org.springframework.data.domain.Page<Book> page) {
        return new PageResponse<>(page.getContent().stream().map(b -> responseBuilder.build(b, true)).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    @Override
    @Transactional(readOnly = true)
    public BookSourceFileResponseDto getSourceFileForAuthor(Long bookId, User author) {
        Book book = bookRepository.findByIdAndAuthor(bookId, author)
                .orElseThrow(() -> {
                    log.warn("SECURITY_ALERT: Unauthorized attempt by User ID {} ({}) to access source file of Book ID {}",
                            author.getId(), author.getEmail(), bookId);
                    return new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_FOUND);
                });

        Attachment pdf = attachmentService.getAttachment(book.getId(), "Book", "PDF_SOURCE")
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.BOOK_OCR_PDF_MISSING));

        Duration ttl = Duration.ofMinutes(3);
        String downloadUrl = fileStorageService.getPreSignedDownloadUrl(pdf.getStoragePath(), ttl, pdf.getFileName());

        log.info("SECURITY_AUDIT: Author User ID {} ({}) generated ephemeral download URL for Book ID {} ({})",
                author.getId(), author.getEmail(), book.getId(), pdf.getFileName());

        return new BookSourceFileResponseDto(
                book.getId(),
                pdf.getFileName(),
                downloadUrl,
                Instant.now().plus(ttl)
        );
    }
}
