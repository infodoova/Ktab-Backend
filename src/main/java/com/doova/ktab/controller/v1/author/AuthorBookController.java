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
@PreAuthorize("hasAnyAuthority('AUTHOR', 'ADMIN', 'ADMIN_LIBRARIAN')")
@Tag(name = "Author Book Management API", description = "Endpoints for authors to publish, manage, and inspect their books.")
public class AuthorBookController {

    private final BookService bookService;
    private final MessageSource messageSource;

    // =========================================================
    // GET BOOKS BY AUTHOR
    // =========================================================
    @Operation(summary = "Get books by author (current author or by author ID)")
    @GetMapping({"/books", "/me/books", "/getBooksByAuthor", "/getBooksByAuthor/{authorId}", "/{authorId}/books"})
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getBooksByAuthor(
            @CurrentUser User author,
            @PathVariable(name = "authorId", required = false) Long pathAuthorId,
            @RequestParam(name = "authorId", required = false) Long paramAuthorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size,
            @RequestParam(required = false) String status) {

        Long targetAuthorId = (pathAuthorId != null) ? pathAuthorId : paramAuthorId;

        PageResponse<BookResponseDto> books = (targetAuthorId != null)
                ? bookService.getBooksByAuthorId(targetAuthorId, page, size, status)
                : bookService.getBooksByAuthorId(author, page, size, status);

        return ResponseUtils.success(books, ApiMessageKey.AUTHOR_BOOKS_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // =========================================================
    // SEARCH AUTHOR'S BOOKS
    // =========================================================
    @Operation(summary = "Search author's catalog with multi-facet filters")
    @PostMapping({"/me/books/search", "/books/search"})
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> searchAuthorBooks(
            @CurrentUser User author,
            @jakarta.validation.Valid @RequestBody com.doova.ktab.dto.book.AuthorBookSearchRequest requestDto
    ) {
        String sortProperty = requestDto.sortBy() != null ? requestDto.sortBy() : "createdAt";
        org.springframework.data.domain.Sort.Direction direction = requestDto.sortDirection() != null ? requestDto.sortDirection() : org.springframework.data.domain.Sort.Direction.DESC;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(requestDto.page(), requestDto.size(), org.springframework.data.domain.Sort.by(direction, sortProperty));

        PageResponse<BookResponseDto> result = bookService.searchAuthorBooks(author, requestDto, pageable);

        return ResponseUtils.success(result, ApiMessageKey.AUTHOR_BOOKS_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // =========================================================
    // GET BOOK BY ID (AUTHOR)
    // =========================================================
    @Operation(summary = "Get book by ID for current author")
    @GetMapping({"/books/{id}", "/book/{id}"})
    public ResponseEntity<ApiResponse<BookResponseDto>> getBookById(@PathVariable Long id, @CurrentUser User author) {
        BookResponseDto book = bookService.getBookByIdForAuthor(id, author);

        return ResponseUtils.success(book, ApiMessageKey.AUTHOR_BOOK_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // =========================================================
    // CREATE BOOK
    // =========================================================
    @Operation(summary = "Create and publish a new book")
    @PostMapping(path = {"/books", "/createBook"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
    @PatchMapping(path = {"/books/{id}", "/updateBook/{id}"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
    @DeleteMapping({"/books/{id}", "/deleteBook/{id}"})
    public ResponseEntity<ApiResponse<Void>> deleteBook(@PathVariable Long id, @CurrentUser User author) {
        bookService.deleteBook(id, author);

        return ResponseUtils.success(null, ApiMessageKey.AUTHOR_BOOK_DELETE_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // =========================================================
    // GET SOURCE FILE (AUDITED EPHEMERAL DOWNLOAD)
    // =========================================================
    @Operation(summary = "Get secure ephemeral download URL for author's own book source file")
    @GetMapping({"/books/{id}/source-file", "/{id}/source-file"})
    @PreAuthorize("hasAnyAuthority('AUTHOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<com.doova.ktab.dto.book.BookSourceFileResponseDto>> getSourceFile(
            @PathVariable Long id,
            @CurrentUser User author
    ) {
        com.doova.ktab.dto.book.BookSourceFileResponseDto response = bookService.getSourceFileForAuthor(id, author);
        return ResponseUtils.success(response, ApiMessageKey.OPERATION_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
