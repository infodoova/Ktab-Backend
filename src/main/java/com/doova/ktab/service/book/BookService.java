package com.doova.ktab.service.book;

import java.util.List;
import com.doova.ktab.dto.book.BookRequestDto;
import com.doova.ktab.dto.book.BookSearchRequestDto;
import com.doova.ktab.dto.book.BookCoverResponse;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface BookService {

    BookResponseDto createBook(BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author);

    BookResponseDto updateBook(Long id, BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author);

    BookResponseDto getBookByIdForAuthor(Long id, User author);

    PageResponse<BookResponseDto> getBooksByAuthorId(User author, int page, int size, String status);

    PageResponse<BookResponseDto> getBooksByAuthorId(Long authorId, int page, int size, String status);

    BookResponseDto getBookById(Long id);

    void deleteBook(Long id, User author);

    BookResponseDto submitForReview(Long id, User author);

    /** Create a new draft book and immediately submit it for review in a single atomic operation. */
    BookResponseDto createAndSubmit(BookRequestDto dto, MultipartFile cover, MultipartFile pdf, User author);

    BookResponseDto withdrawFromReview(Long id, User author);

    PageResponse<BookResponseDto> getAllBooksPaginated(int page, int size);

    PageResponse<BookResponseDto> searchBooks(BookSearchRequestDto req, Pageable pageable);

    PageResponse<BookResponseDto> advancedSearchBooks(com.doova.ktab.dto.book.AdvancedBookSearchRequest req, Pageable pageable);

    PageResponse<BookResponseDto> searchAuthorBooks(User author, com.doova.ktab.dto.book.AuthorBookSearchRequest req, Pageable pageable);

    PageResponse<BookCoverResponse> getBookCovers(int page, int size);

    /** Cover image URLs only, of the best-rated published books that have at least one review (best first). */
    List<String> getTopReviewedCoverImages(int limit);

    /** The same books as {@link #getTopReviewedCoverImages}, in the same order, as full cover responses with their about-the-book audio. */
    List<BookCoverResponse> getTopReviewedBooks(int limit);

    /** Cover image URLs only, of the published books, newest first. */
    PageResponse<String> getCoverImages(int page, int size);

    com.doova.ktab.dto.book.BookSourceFileResponseDto getSourceFileForAuthor(Long bookId, User author);
}
