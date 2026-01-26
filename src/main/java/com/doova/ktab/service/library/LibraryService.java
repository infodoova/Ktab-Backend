package com.doova.ktab.service.library;

import com.doova.ktab.dto.request.AssignBookRequest;
import com.doova.ktab.dto.response.BookResponseDto;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.enums.LibrarySort;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.book.BookLibraryEntry;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookLibraryEntryRepository;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.helpers.BookResponseBuilderService;
import com.doova.ktab.utils.PageResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final BookLibraryEntryRepository libraryRepository;
    private final BookResponseBuilderService responseBuilder;

    // ------------------------------------------------------------
    // ASSIGN BOOK
    // ------------------------------------------------------------
    public void assignBookToUser(AssignBookRequest request, User reader) {

        Book book = bookRepository.findById(request.bookId()).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.LIBRARY_BOOK_NOT_FOUND.getKey()));

        if (libraryRepository.existsByUserIdAndBookId(reader.getId(), book.getId())) {
            throw new IllegalStateException(ApiMessageKey.LIBRARY_BOOK_ALREADY_EXISTS.getKey());
        }

        BookLibraryEntry entry = new BookLibraryEntry();
        entry.setUser(reader);
        entry.setBook(book);

        libraryRepository.save(entry);
    }

    // ------------------------------------------------------------
    // GET FULL LIBRARY
    // ------------------------------------------------------------
    public List<BookResponseDto> getUserLibrary(Long userId) {

        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException(ApiMessageKey.LIBRARY_USER_NOT_FOUND.getKey());
        }

        return libraryRepository.findAllByUserId(userId).stream().map(e -> responseBuilder.build(e.getBook())).toList();
    }

    // ------------------------------------------------------------
    // GET PAGINATED LIBRARY
    // ------------------------------------------------------------
    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getUserLibrary(Long userId, int page, int size, LibrarySort sort) {

        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException(ApiMessageKey.LIBRARY_USER_NOT_FOUND.getKey());
        }

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));

        Page<BookLibraryEntry> entryPage = libraryRepository.findAllByUserId(userId, pageable);

        List<BookResponseDto> books = entryPage.getContent().stream().map(e -> responseBuilder.build(e.getBook())).toList();

        return new PageResponse<>(books, entryPage.getNumber(), entryPage.getSize(), entryPage.getTotalElements(), entryPage.getTotalPages(), entryPage.isLast());
    }

    // ------------------------------------------------------------
    // REMOVE BOOK
    // ------------------------------------------------------------
    public void removeBookFromLibrary(Long userId, Long bookId) {

        BookLibraryEntry entry = libraryRepository.findByUserIdAndBookId(userId, bookId).orElseThrow(() -> new EntityNotFoundException(ApiMessageKey.LIBRARY_BOOK_NOT_IN_LIBRARY.getKey()));

        libraryRepository.delete(entry);
    }

    // ------------------------------------------------------------
    // CHECK ASSIGNMENT
    // ------------------------------------------------------------
    @Transactional(readOnly = true)
    public boolean isAssigned(Long userId, Long bookId) {

        if (!bookRepository.existsById(bookId)) {
            throw new EntityNotFoundException(ApiMessageKey.LIBRARY_BOOK_NOT_FOUND.getKey());
        }

        return libraryRepository.existsByUserIdAndBookId(userId, bookId);
    }

    private Sort resolveSort(LibrarySort sort) {
        return switch (sort) {
            case RECENT -> Sort.by(Sort.Direction.DESC, "audit.createdAt");
            case TITLE_ASC -> Sort.by(Sort.Direction.ASC, "book.title");
            case TITLE_DESC -> Sort.by(Sort.Direction.DESC, "book.title");
            case RATING_ASC -> Sort.by(Sort.Direction.ASC, "book.averageRating");
            case RATING_DESC -> Sort.by(Sort.Direction.DESC, "book.averageRating");
        };
    }
}
