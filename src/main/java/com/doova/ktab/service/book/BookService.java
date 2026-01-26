package com.doova.ktab.service.book;

import com.doova.ktab.dto.request.BookRequestDto;
import com.doova.ktab.dto.request.BookSearchRequestDto;
import com.doova.ktab.dto.response.BookResponseDto;
import com.doova.ktab.dto.event.BookPublishedEvent;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.mappers.book.BookMapper;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.interfaces.BookFileService;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.helpers.BookResponseBuilderService;
import com.doova.ktab.utils.PageResponse;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;
    private final BookFileService bookFileService;
    private final EntityManager entityManager;
    private final BookResponseBuilderService responseBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final AttachmentService attachmentService;

    // =========================================================
    // FILTER
    // =========================================================
    private void enablePublishedFilter() {
        entityManager.unwrap(Session.class).enableFilter("publishedFilter").setParameter("status", BookStatus.PUBLISHED.name());
    }

    // =========================================================
    // CREATE
    // =========================================================
    @Transactional
    public BookResponseDto createBook(BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author) {
        Book book = bookMapper.toEntity(dto);
        book.setAuthor(author);

        Book savedBook = bookRepository.save(book);
        // Files must be handled before we try to get the pdfKey
        bookFileService.handleCreateFiles(savedBook, cover, pdf);

        // If created directly as PUBLISHED, trigger OCR
//        if (savedBook.getStatus() == BookStatus.PUBLISHED) {
//            String pdfKey = getPdfKey(savedBook.getId());
//            eventPublisher.publishEvent(new BookPublishedEvent(savedBook.getId(), pdfKey));
//        }

        return responseBuilder.build(savedBook);
    }

    // =========================================================
    // UPDATE
    // =========================================================
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

        return responseBuilder.build(savedBook);
    }

    // =========================================================
    // GET FOR AUTHOR
    // =========================================================
    @Transactional(readOnly = true)
    public BookResponseDto getBookByIdForAuthor(Long id, User author) {

        Book book = bookRepository.findByIdAndAuthor(id, author).orElseThrow(() -> new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_FOUND));

        return responseBuilder.build(book);
    }

    // =========================================================
    // GET BY AUTHOR
    // =========================================================
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getBooksByAuthorId(Long authorId, int page, int size, String status) {
        var pageable = PageRequest.of(page, size, Sort.by("id").descending());

        var pageResult = StringUtils.hasText(status) ? bookRepository.findAllByAuthorIdAndStatus(authorId, BookStatus.valueOf(status.toUpperCase()), pageable) : bookRepository.findAllByAuthorId(authorId, pageable);

        return mapBookPage(pageResult);
    }

    // GET BOOK BY ID (READER)
    @Transactional(readOnly = true)
    public BookResponseDto getBookById(Long id) {

        enablePublishedFilter();

        Book book = bookRepository.findById(id).orElseThrow(() -> new BadRequestException(ApiMessageKey.READER_BOOK_NOT_FOUND));

        return responseBuilder.build(book);
    }


    // =========================================================
    // DELETE
    // =========================================================
    @Transactional
    public void deleteBook(Long id, User author) {

        Book book = bookRepository.findByIdAndAuthor(id, author).orElseThrow(() -> new BadRequestException(ApiMessageKey.AUTHOR_BOOK_NOT_OWNER));

        bookFileService.handleDeleteFiles(book);
        bookRepository.delete(book);
    }

    // =========================================================
    // READER – GET ALL PUBLISHED BOOKS (PAGINATED)
    // =========================================================
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getAllBooksPaginated(int page, int size) {

        enablePublishedFilter();

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishDate"));

        var booksPage = bookRepository.findAll(pageable);

        return new PageResponse<>(booksPage.getContent().stream().map(responseBuilder::build).toList(), booksPage.getNumber(), booksPage.getSize(), booksPage.getTotalElements(), booksPage.getTotalPages(), booksPage.isLast());
    }


    // =========================================================
    // READER – SEARCH BOOKS (CRITERIA QUERY)
    // =========================================================
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

    private String getPdfKey(Long bookId) {
        return attachmentService.getAttachment(bookId, "Book", "PDF_SOURCE")
                .map(Attachment::getStoragePath)
                .orElseThrow(() -> new IllegalStateException("PDF_SOURCE attachment missing for published book: " + bookId));
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

        return predicates;
    }


    // =========================================================
    // HELPER
    // =========================================================
    private PageResponse<BookResponseDto> mapBookPage(org.springframework.data.domain.Page<Book> page) {
        return new PageResponse<>(page.getContent().stream().map(responseBuilder::build).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
