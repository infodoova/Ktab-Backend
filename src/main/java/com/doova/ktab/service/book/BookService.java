package com.doova.ktab.service.book;

import com.doova.ktab.api.dto.request.BookRequestDto;
import com.doova.ktab.api.dto.request.BookSearchRequestDto;
import com.doova.ktab.api.dto.response.BookResponseDto;
import com.doova.ktab.enums.BookStatus;
import com.doova.ktab.mappers.book.BookMapper;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.book.interfaces.BookFileService;
import com.doova.ktab.service.helpers.BookResponseBuilderService;
import com.doova.ktab.utils.PageResponse;
import jakarta.persistence.*;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
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

    // =========================================================
    // FILTER HELPER
    // =========================================================
    private void enablePublishedFilter() {
        entityManager.unwrap(Session.class)
                .enableFilter("publishedFilter")
                .setParameter("status", BookStatus.PUBLISHED.name());
    }

    // =========================================================
    // CREATE (AUTHOR)
    // =========================================================
    @Transactional
    public BookResponseDto createBook(BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author)
            throws IOException {

        Book book = bookMapper.toEntity(dto);
        book.setAuthor(author);

        bookRepository.save(book);
        bookFileService.handleCreateFiles(book, cover, pdf);

        return responseBuilder.build(book);
    }

    // =========================================================
    // UPDATE (AUTHOR)
    // =========================================================
    @Transactional
    public BookResponseDto updateBook(Long id, BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author)
            throws IOException {

        Book book = bookRepository.findByIdAndAuthor(id, author)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));

        if (book.getStatus() == BookStatus.PUBLISHED) {
            throw new IllegalStateException("Published books cannot be updated");
        }

        bookMapper.updateBookFromDto(dto, book);
        bookFileService.handleUpdateFiles(book, cover, pdf);

        return responseBuilder.build(bookRepository.save(book));
    }

    // =========================================================
    // READER – GET BY ID (FILTER ENABLED)
    // =========================================================
    @Transactional(readOnly = true)
    public BookResponseDto getBookById(Long id) {
        enablePublishedFilter();

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Book not found"));

        return responseBuilder.build(book);
    }

    // =========================================================
    // READER – PAGINATION (FILTER ENABLED)
    // =========================================================
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getAllBooksPaginated(int page, int size) {
        enablePublishedFilter();

        Page<Book> books = bookRepository.findAll(PageRequest.of(page, size));
        return mapBookPage(books);
    }

    // =========================================================
    // AUTHOR – GET BY AUTHOR (NO FILTER)
    // =========================================================
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getBooksByAuthorId(Long authorId, int page, int size, String status) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Page<Book> pageResult;

        if (StringUtils.hasText(status)) {
            pageResult = bookRepository.findAllByAuthorIdAndStatus(
                    authorId,
                    BookStatus.valueOf(status.toUpperCase()),
                    pageable
            );
        } else {
            pageResult = bookRepository.findAllByAuthorId(authorId, pageable);
        }

        return mapBookPage(pageResult);
    }

    // =========================================================
    // READER – SEARCH (FILTER ENABLED)
    // =========================================================
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> searchBooks(BookSearchRequestDto req, Pageable pageable) {

        enablePublishedFilter();

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Book> query = cb.createQuery(Book.class);
        Root<Book> root = query.from(Book.class);

        List<Predicate> predicates = new ArrayList<>();

        if (StringUtils.hasText(req.title())) {
            predicates.add(cb.like(cb.lower(root.get("title")), "%" + req.title().toLowerCase() + "%"));
        }

        if (req.genres() != null && !req.genres().isEmpty()) {
            predicates.add(root.get("genre").in(req.genres()));
        }

        if (req.age() != null) {
            predicates.add(cb.le(root.get("ageRangeMin"), req.age()));
            predicates.add(cb.ge(root.get("ageRangeMax"), req.age()));
        }

        if (req.minAverageRating() != null) {
            predicates.add(cb.ge(root.get("averageRating"), req.minAverageRating()));
        }

        query.where(predicates.toArray(Predicate[]::new));
        query.orderBy(cb.desc(root.get("averageRating")));

        TypedQuery<Book> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Book> books = typedQuery.getResultList();

        Page<Book> page = new PageImpl<>(books, pageable, books.size());
        return mapBookPage(page);
    }

    // =========================================================
    // DELETE (AUTHOR)
    // =========================================================
    @Transactional
    public void deleteBook(Long id, User author) {

        Book book = bookRepository.findByIdAndAuthor(id, author)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));

        bookFileService.handleDeleteFiles(book);
        bookRepository.delete(book);
    }

    // =========================================================
    // HELPER
    // =========================================================
    private PageResponse<BookResponseDto> mapBookPage(Page<Book> page) {
        return new PageResponse<>(
                page.getContent().stream().map(responseBuilder::build).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
