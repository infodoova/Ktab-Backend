package com.doova.ktab.service.librarian;

import com.doova.ktab.dto.library.LibrarianBookUploadRequest;
import com.doova.ktab.dto.library.UpdateLibrarianBookRequest;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.pagination.PageResponse;
import org.springframework.web.multipart.MultipartFile;

public interface LibrarianBookService {

    BookResponseDto createBook(
            LibrarianBookUploadRequest req,
            MultipartFile cover,
            MultipartFile pdf,
            User librarian
    );

    BookResponseDto updateBook(
            Long id,
            UpdateLibrarianBookRequest req,
            MultipartFile cover,
            MultipartFile pdf,
            User librarian
    );

    BookResponseDto getBookByIdForLibrarian(Long id, User librarian);

    PageResponse<BookResponseDto> getBooksForLibrary(int page, int size, String status, User librarian);

    void deleteBook(Long id, User librarian);

    PageResponse<BookResponseDto> getPublishedBooksByLibrary(Long libraryOrgId, int page, int size);

    com.doova.ktab.dto.book.BookSourceFileResponseDto getSourceFileForLibrarian(Long bookId, User librarian);
}
