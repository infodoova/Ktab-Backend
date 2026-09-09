package com.doova.ktab.service.library.impl;

import com.doova.ktab.dto.library.AssignBookRequest;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.library.LibrarySort;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookLibraryEntry;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookLibraryEntryRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.book.BookResponseBuilderService;
import com.doova.ktab.service.library.LibraryService;
import com.doova.ktab.utils.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LibraryServiceImpl implements LibraryService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BookLibraryEntryRepository libraryRepository;
    private final BookResponseBuilderService responseBuilder;

    // ------------------------------------------------------------
    // ASSIGN BOOK
    // ------------------------------------------------------------
    @Override
    @Transactional
    public void assignBookToUser(AssignBookRequest request, User reader) {

        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARY_BOOK_NOT_FOUND));

        if (libraryRepository.existsByUserIdAndBookId(reader.getId(), book.getId())) {
            throw new BadRequestException(ApiMessageKey.LIBRARY_BOOK_ALREADY_EXISTS);
        }

        BookLibraryEntry entry = new BookLibraryEntry();
        entry.setUser(reader);
        entry.setBook(book);

        libraryRepository.save(entry);
    }

    // ------------------------------------------------------------
    // GET FULL LIBRARY
    // ------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public List<BookResponseDto> getUserLibrary(Long userId) {

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(ApiMessageKey.LIBRARY_USER_NOT_FOUND);
        }

        return libraryRepository.findAllByUserId(userId)
                .stream()
                .map(e -> responseBuilder.build(e.getBook()))
                .toList();
    }

    // ------------------------------------------------------------
    // GET PAGINATED LIBRARY
    // ------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getUserLibrary(Long userId, int page, int size, LibrarySort sort) {

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(ApiMessageKey.LIBRARY_USER_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));

        Page<BookLibraryEntry> entryPage = libraryRepository.findAllByUserId(userId, pageable);

        List<BookResponseDto> books = entryPage.getContent()
                .stream()
                .map(e -> responseBuilder.build(e.getBook()))
                .toList();

        return new PageResponse<>(
                books,
                entryPage.getNumber(),
                entryPage.getSize(),
                entryPage.getTotalElements(),
                entryPage.getTotalPages(),
                entryPage.isLast()
        );
    }

    // ------------------------------------------------------------
    // REMOVE BOOK
    // ------------------------------------------------------------
    @Override
    @Transactional
    public void removeBookFromLibrary(Long userId, Long bookId) {

        BookLibraryEntry entry = libraryRepository.findByUserIdAndBookId(userId, bookId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARY_BOOK_NOT_IN_LIBRARY));

        libraryRepository.delete(entry);
    }

    // ------------------------------------------------------------
    // CHECK ASSIGNMENT
    // ------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public boolean isAssigned(Long userId, Long bookId) {

        if (!bookRepository.existsById(bookId)) {
            throw new ResourceNotFoundException(ApiMessageKey.LIBRARY_BOOK_NOT_FOUND);
        }

        return libraryRepository.existsByUserIdAndBookId(userId, bookId);
    }

    private Sort resolveSort(LibrarySort sort) {
        return switch (sort) {
            case RECENT -> Sort.by(Sort.Direction.DESC, "createdAt");
            case TITLE_ASC -> Sort.by(Sort.Direction.ASC, "book.title");
            case TITLE_DESC -> Sort.by(Sort.Direction.DESC, "book.title");
            case RATING_ASC -> Sort.by(Sort.Direction.ASC, "book.averageRating");
            case RATING_DESC -> Sort.by(Sort.Direction.DESC, "book.averageRating");
        };
    }
}
