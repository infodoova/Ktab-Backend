package com.doova.ktab.controller.v1.author;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.book.BookRequestDto;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import com.doova.ktab.validation.CreateBook;
import com.doova.ktab.validation.UpdateBook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/authors", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('AUTHOR')")
@Tag(name = "Author Book Management API", description = "Endpoints for authors to publish, manage, and inspect their books.")
public class AuthorBookController {

    private final BookService bookService;
    private final MessageSource messageSource;

    // =========================================================
    // GET BOOKS BY AUTHOR
    // =========================================================
    @Operation(summary = "Get books for current author (or filter by author ID)")
    @GetMapping("/books")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getBooksByAuthor(
            @CurrentUser User author,
            @RequestParam(required = false) Long authorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @RequestParam(required = false) String status) {

        PageResponse<BookResponseDto> books = (authorId != null)
                ? bookService.getBooksByAuthorId(authorId, page, size, status)
                : bookService.getBooksByAuthorId(author, page, size, status);

        return ResponseUtils.success(books, ApiMessageKey.AUTHOR_BOOKS_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // =========================================================
    // GET BOOK BY ID (AUTHOR)
    // =========================================================
    @Operation(summary = "Get book by ID for current author")
    @GetMapping("/books/{id}")
    public ResponseEntity<ApiResponse<BookResponseDto>> getBookById(@PathVariable Long id, @CurrentUser User author) {
        BookResponseDto book = bookService.getBookByIdForAuthor(id, author);

        return ResponseUtils.success(book, ApiMessageKey.AUTHOR_BOOK_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // =========================================================
    // CREATE BOOK
    // =========================================================
    @Operation(summary = "Create and publish a new book")
    @PostMapping(path = "/books", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BookResponseDto>> createBook(
            @Validated(CreateBook.class) @RequestPart("bookDto") BookRequestDto bookDto,
            @RequestPart("coverImage") MultipartFile coverImage,
            @RequestPart("pdfFile") MultipartFile pdfFile,
            @CurrentUser User author
    ) {
        BookResponseDto created = bookService.createBook(bookDto, coverImage, pdfFile, author);

        return ResponseUtils.success(created, ApiMessageKey.AUTHOR_BOOK_CREATE_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    // =========================================================
    // UPDATE BOOK
    // =========================================================
    @Operation(summary = "Update an existing book")
    @PatchMapping(path = "/books/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BookResponseDto>> updateBook(
            @PathVariable Long id,
            @Validated(UpdateBook.class) @RequestPart("bookDto") BookRequestDto bookDto,
            @RequestPart(value = "coverImage", required = false) MultipartFile coverImage,
            @RequestPart(value = "pdfFile", required = false) MultipartFile pdfFile,
            @CurrentUser User author
    ) {
        BookResponseDto updated = bookService.updateBook(id, bookDto, coverImage, pdfFile, author);

        return ResponseUtils.success(updated, ApiMessageKey.AUTHOR_BOOK_UPDATE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // =========================================================
    // DELETE BOOK
    // =========================================================
    @Operation(summary = "Delete an author book")
    @DeleteMapping("/books/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id, @CurrentUser User author) {
        bookService.deleteBook(id, author);

        return ResponseUtils.success(null, ApiMessageKey.AUTHOR_BOOK_DELETE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
