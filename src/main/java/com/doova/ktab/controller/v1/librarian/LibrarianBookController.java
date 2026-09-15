package com.doova.ktab.controller.v1.librarian;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.library.LibrarianBookUploadRequest;
import com.doova.ktab.dto.library.UpdateLibrarianBookRequest;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.librarian.LibrarianBookService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/librarians/books", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('LIBRARIAN', 'ADMIN_LIBRARIAN', 'ADMIN')")
@Tag(name = "Librarian Book Management API", description = "Endpoints for librarians to upload, update, and manage books on behalf of their library organization.")
public class LibrarianBookController {

    private final LibrarianBookService librarianBookService;
    private final MessageSource messageSource;

    @Operation(summary = "Upload a book for the librarian's library organization")
    @PostMapping(path = {"", "/createBook"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BookResponseDto>> createBook(
            @Valid @RequestPart("bookDto") LibrarianBookUploadRequest bookDto,
            @RequestPart("coverImage") MultipartFile coverImage,
            @RequestPart("pdfFile") MultipartFile pdfFile,
            @CurrentUser User librarian
    ) {
        BookResponseDto created = librarianBookService.createBook(bookDto, coverImage, pdfFile, librarian);
        return ResponseUtils.success(created, ApiMessageKey.LIBRARIAN_BOOK_CREATE_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a library book")
    @PatchMapping(path = {"/{id}", "/updateBook/{id}"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BookResponseDto>> updateBook(
            @PathVariable Long id,
            @Valid @RequestPart(value = "bookDto", required = false) UpdateLibrarianBookRequest bookDto,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "pdfFile", required = false) MultipartFile pdfFile,
            @CurrentUser User librarian
    ) {
        UpdateLibrarianBookRequest dto = bookDto != null ? bookDto : new UpdateLibrarianBookRequest();
        BookResponseDto updated = librarianBookService.updateBook(id, dto, coverImage, pdfFile, librarian);
        return ResponseUtils.success(updated, ApiMessageKey.LIBRARIAN_BOOK_UPDATE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get a single library book by ID")
    @GetMapping({"/{id}", "/viewBook/{id}"})
    public ResponseEntity<ApiResponse<BookResponseDto>> getBookById(
            @PathVariable Long id,
            @CurrentUser User librarian
    ) {
        BookResponseDto book = librarianBookService.getBookByIdForLibrarian(id, librarian);
        return ResponseUtils.success(book, ApiMessageKey.LIBRARIAN_BOOK_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "List all books belonging to the librarian's organization")
    @GetMapping({"", "/books", "/me/books", "/viewBooks"})
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getMyLibraryBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @CurrentUser User librarian
    ) {
        PageResponse<BookResponseDto> books = librarianBookService.getBooksForLibrary(page, size, status, librarian);
        return ResponseUtils.success(books, ApiMessageKey.LIBRARIAN_BOOKS_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Delete a book from the library organization")
    @DeleteMapping({"/{id}", "/deleteBook/{id}"})
    public ResponseEntity<ApiResponse<Void>> deleteBook(
            @PathVariable Long id,
            @CurrentUser User librarian
    ) {
        librarianBookService.deleteBook(id, librarian);
        return ResponseUtils.success(null, ApiMessageKey.LIBRARIAN_BOOK_DELETE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get secure ephemeral download URL for library book source file")
    @GetMapping({"/{id}/source-file", "/books/{id}/source-file"})
    @PreAuthorize("hasAnyAuthority('LIBRARIAN', 'ADMIN_LIBRARIAN', 'ADMIN')")
    public ResponseEntity<ApiResponse<com.doova.ktab.dto.book.BookSourceFileResponseDto>> getSourceFile(
            @PathVariable Long id,
            @CurrentUser User librarian
    ) {
        com.doova.ktab.dto.book.BookSourceFileResponseDto response = librarianBookService.getSourceFileForLibrarian(id, librarian);
        return ResponseUtils.success(response, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
