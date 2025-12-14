package com.doova.ktab.service.library;

import com.doova.ktab.api.dto.request.AssignBookRequest;
import com.doova.ktab.api.dto.response.BookResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

        Book book = bookRepository.findById(request.bookId()).orElseThrow(() -> new EntityNotFoundException("Book not found"));

        if (libraryRepository.existsByUserIdAndBookId(reader.getId(), book.getId())) {
            throw new IllegalStateException("Book is already assigned to this user.");
        }

        BookLibraryEntry entry = new BookLibraryEntry();
        entry.setUser(reader);
        entry.setBook(book);

        libraryRepository.save(entry);
    }

    // ------------------------------------------------------------
    // GET USER LIBRARY
    // ------------------------------------------------------------
    public List<BookResponseDto> getUserLibrary(Long userId) {

        if (!userRepository.existsById(userId)) throw new EntityNotFoundException("User not found with ID: " + userId);

        List<BookLibraryEntry> entries = libraryRepository.findAllByUserId(userId);

        return entries.stream().map(entry -> responseBuilder.build(entry.getBook())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BookResponseDto> getUserLibrary(Long userId, int page, int size, LibrarySort sort) {

        if (!userRepository.existsById(userId)) throw new EntityNotFoundException("User not found");

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));

        Page<BookLibraryEntry> entryPage = libraryRepository.findAllByUserId(userId, pageable);

        List<BookResponseDto> books = entryPage.getContent().stream().map(entry -> responseBuilder.build(entry.getBook())).toList();

        return new PageResponse<>(books, entryPage.getNumber(), entryPage.getSize(), entryPage.getTotalElements(), entryPage.getTotalPages(), entryPage.isLast());
    }

    // ------------------------------------------------------------
    // REMOVE BOOK
    // ------------------------------------------------------------
    public void removeBookFromLibrary(Long userId, Long bookId) {

        BookLibraryEntry entry = libraryRepository.findByUserIdAndBookId(userId, bookId).orElseThrow(() -> new EntityNotFoundException("Book is not in user's library."));

        libraryRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public boolean isAssigned(Long userId, Long bookId) {

        if (!bookRepository.existsById(bookId)) throw new EntityNotFoundException("Book not found");

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
