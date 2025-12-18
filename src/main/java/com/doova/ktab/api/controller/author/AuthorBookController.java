package com.doova.ktab.api.controller.author;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.annotation.UserMatchesOrAdmin;
import com.doova.ktab.api.dto.request.BookRequestDto;
import com.doova.ktab.api.dto.response.BookResponseDto;
import com.doova.ktab.api.validation.CreateBook;
import com.doova.ktab.api.validation.UpdateBook;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.exceptions.S3UploadException;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.utils.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/authors", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Author Book Management API", description = "Author endpoints for managing books, including S3 file handling.")
public class AuthorBookController {

    private final BookService bookService;

    // ============================================================================================
    // GET BOOKS BY AUTHOR
    // ============================================================================================
    @Operation(summary = "Get books by author ID (paginated)", description = "Retrieves paginated books written by a specific author.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Books retrieved", content = @Content(schema = @Schema(implementation = PageResponse.class)))
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @UserMatchesOrAdmin(path = "#authorId")
    @GetMapping("/getBooksByAuthor/{authorId}")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getBooksByAuthor(@PathVariable Long authorId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "6") int size, @RequestParam String status) {
        PageResponse<BookResponseDto> books = bookService.getBooksByAuthorId(authorId, page, size, status);
        return ResponseUtils.response(books);
    }

    // ============================================================================================
    // GET BOOK BY ID (AUTHOR)
    // ============================================================================================
    @Operation(summary = "Get book by ID (author)", description = "Retrieves a single book by ID for the author (any status).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book retrieved", content = @Content(schema = @Schema(implementation = BookResponseDto.class)))
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @GetMapping("/book/{id}")
    public ResponseEntity<ApiResponse<BookResponseDto>> getBookById(@PathVariable Long id, @CurrentUser User author) {
        try {
            BookResponseDto book = bookService.getBookByIdForAuthor(id, author);
            return ResponseUtils.response(book);
        } catch (EntityNotFoundException ex) {
            throw ResponseUtils.notFound(ex.getMessage());
        }
    }

    // ============================================================================================
    // CREATE BOOK (MULTIPART)
    // ============================================================================================
    @Operation(summary = "Create a new book", description = "Uploads cover image + PDF to S3 and creates a new book.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Book created", content = @Content(schema = @Schema(implementation = BookResponseDto.class)))
    @PostMapping(path = "createBook", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    public ResponseEntity<ApiResponse<BookResponseDto>> createBook(@Validated(CreateBook.class) @RequestPart("bookDto") BookRequestDto bookDto, @RequestPart("coverImage") MultipartFile coverImage, @RequestPart("pdfFile") MultipartFile pdfFile, @CurrentUser User author) {
        try {
            BookResponseDto created = bookService.createBook(bookDto, coverImage, pdfFile, author);
            return ResponseUtils.created(created);
        } catch (EntityNotFoundException ex) {
            throw ResponseUtils.errorResponse("Invalid author ID", HttpStatus.BAD_REQUEST);
        } catch (IOException ex) {
            throw ResponseUtils.errorResponse("File upload failed: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (S3UploadException ex) {
            throw ResponseUtils.errorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ============================================================================================
    // UPDATE BOOK (MULTIPART)
    // ============================================================================================
    @Operation(summary = "Update a book", description = "Updates book data and optionally replaces S3 files.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book updated", content = @Content(schema = @Schema(implementation = BookResponseDto.class)))
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @PatchMapping(path = "updateBook/{id}", consumes = {"multipart/form-data"})
    public ResponseEntity<ApiResponse<BookResponseDto>> updateBook(@PathVariable Long id, @Validated(UpdateBook.class) @RequestPart("bookDto") BookRequestDto bookDto, @RequestPart(value = "coverImage", required = false) MultipartFile coverImage, @RequestPart(value = "pdfFile", required = false) MultipartFile pdfFile, @CurrentUser User author) {
        try {
            BookResponseDto updated = bookService.updateBook(id, bookDto, coverImage, pdfFile, author);
            return ResponseUtils.response(updated, "Book updated successfully");
        } catch (EntityNotFoundException ex) {
            throw ResponseUtils.notFound(ex.getMessage());
        } catch (IOException ex) {
            throw ResponseUtils.errorResponse("File update failed: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    // ============================================================================================
    // DELETE BOOK
    // ============================================================================================
    @Operation(summary = "Delete book", description = "Deletes book and removes S3 files.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Book deleted")
    @PreAuthorize("hasAnyAuthority('AUTHOR')")
    @DeleteMapping("deleteBook/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteBook(@PathVariable Long id, @CurrentUser User author) {
        try {
            bookService.deleteBook(id, author);
            return ResponseUtils.response(null, "Book deleted successfully");
        } catch (EntityNotFoundException ex) {
            throw ResponseUtils.notFound("Book not found");
        }
    }
}
