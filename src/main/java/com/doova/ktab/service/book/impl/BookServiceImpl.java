package com.doova.ktab.service.book.impl;

import com.doova.ktab.event.model.BookPublishedEvent;
import com.doova.ktab.dto.book.BookRequestDto;
import com.doova.ktab.dto.book.BookSearchRequestDto;
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
import com.doova.ktab.service.book.BookService;
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
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.ArrayList;
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
    private final ApplicationEventPublisher eventPublisher;
    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;

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
        Book book = bookRepository.findByIdAndAuthor(id, author)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_OWNER));

        // Prevent editing already published books if that's your business rule
        if (book.getStatus() == BookStatus.PUBLISHED) {
            throw new BadRequestException(ApiMessageKey.AUTHOR_BOOK_UPDATE_FORBIDDEN);
        }

        bookMapper.updateBookFromDto(dto, book);
        bookFileService.handleUpdateFiles(book, cover, pdf);

        Book savedBook = bookRepository.save(book);

        // If updated to PUBLISHED, trigger OCR
        if (savedBook.getStatus() == BookStatus.PUBLISHED) {
            String pdfKey = getPdfKey(savedBook.getId());
            eventPublisher.publishEvent(new BookPublishedEvent(savedBook.getId(), pdfKey));
        }

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

        bookFileService.handleDeleteFiles(book);
        bookRepository.delete(book);
    }

    // =========================================================
    // READER – GET ALL PUBLISHED BOOKS (PAGINATED)
    // =========================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getAllBooksPaginated(int page, int size) {
        enablePublishedFilter();

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishDate"));

        var booksPage = bookRepository.findAllByBookSource(BookSource.AUTHOR, pageable);

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

    private String getPdfKey(Long bookId) {
        return attachmentService.getAttachment(bookId, "Book", "PDF_SOURCE")
                .map(Attachment::getStoragePath)
                .orElseThrow(() -> new BadRequestException(ApiMessageKey.BOOK_OCR_PDF_MISSING));
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

        // BOOK SOURCE: Only return author books, exclude librarian / institutional books
        BookSource source = req.bookSource() != null ? req.bookSource() : BookSource.AUTHOR;
        predicates.add(cb.equal(root.get("bookSource"), source));

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
        var booksPage = bookRepository.findAllByBookSource(BookSource.AUTHOR, pageable);

        List<BookCoverResponse> covers = booksPage.getContent().stream()
                .map(book -> {
                    String coverUrl = attachmentService
                            .getAttachment(book.getId(), BookResponseBuilderService.BOOK_ENTITY_TYPE, BookResponseBuilderService.COVER_IMAGE_TYPE)
                            .map(attachment -> fileStorageService.getFileUrl(attachment.getStoragePath(), UrlStrategy.SIGNED))
                            .orElse(null);

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
                            .build();
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
