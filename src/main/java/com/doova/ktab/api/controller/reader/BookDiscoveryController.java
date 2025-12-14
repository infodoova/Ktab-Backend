package com.doova.ktab.api.controller.reader;

import com.doova.ktab.api.dto.request.BookSearchRequestDto;
import com.doova.ktab.api.dto.response.BookResponseDto;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.utils.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// NOTE: This class was renamed from ReaderBookController to reflect its core responsibility.

@RestController
@RequestMapping(path = "/api/v1/reader", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Reader Book Discovery API", description = "Public endpoints for accessing, searching, and discovering books.")
public class BookDiscoveryController {

    private final BookService bookService;

    // ============================================================================================
    // GET ALL BOOKS (PAGINATED)
    // ============================================================================================
    @Operation(summary = "Get all books (paginated)", description = "Retrieves all published books. Accessible by anyone.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Books retrieved", content = @Content(schema = @Schema(implementation = PageResponse.class)))
    @GetMapping("/viewBooks")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getAllBooks(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        PageResponse<BookResponseDto> books = bookService.getAllBooksPaginated(page, size);
        return ResponseUtils.response(books);
    }


    // ============================================================================================
    // SEARCH BOOKS (CRITERIA QUERY)
    // ============================================================================================
    @Operation(summary = "Search books by criteria (paginated)", description = "Performs dynamic search on title, genre, age range, and minimum rating.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Books matching criteria retrieved", content = @Content(schema = @Schema(implementation = PageResponse.class)))
    @PostMapping("/search")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> searchBooks(@RequestBody BookSearchRequestDto requestDto) {
        Pageable pageable = PageRequest.of(requestDto.page(), requestDto.size());
        PageResponse<BookResponseDto> matchedBooks = bookService.searchBooks(requestDto, pageable);
        return ResponseUtils.response(matchedBooks);
    }


    // ============================================================================================
    // GET BOOK BY ID
    // ============================================================================================
    @Operation(summary = "Get book by ID", description = "Retrieves a single book's details by its unique ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Book retrieved successfully", content = @Content(schema = @Schema(implementation = BookResponseDto.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Book not found")
    @GetMapping("/viewBook/{id}")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<BookResponseDto>> getBookById(@PathVariable Long id) {
        try {
            BookResponseDto bookDto = bookService.getBookById(id);
            return ResponseUtils.response(bookDto);
        } catch (EntityNotFoundException ex) {
            throw ResponseUtils.notFound("Book not found with ID: " + id);
        }
    }

    // ============================================================================================
    // GET SIMILAR BOOKS
    // ============================================================================================
//    @Operation(summary = "Get similar books", description = "Finds books similar to the given book based on genre and overlapping age range.")
//    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Similar books retrieved", content = @Content(schema = @Schema(implementation = PageResponse.class)))
//    @GetMapping("/similar/{bookId}")
//    @PreAuthorize("hasAnyAuthority('READER')")
//    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getSimilarBooks(@PathVariable Long bookId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
//        PageResponse<BookResponseDto> similarBooks = bookService.getSimilarBooks(bookId, page, size);
//        return ResponseUtils.response(similarBooks);
//    }
}