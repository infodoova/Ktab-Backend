package com.doova.ktab.controller.v1.reader;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.book.BookCoverResponse;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSearchRequestDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.service.book.BookSimilarityService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/reader", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Reader Book Discovery API", description = "Endpoints for accessing, searching, and discovering books.")
public class BookDiscoveryController {

    private final BookService bookService;
    private final BookSimilarityService similarityService;
    private final MessageSource messageSource;

    // ============================================================================================
    // GET ALL BOOKS
    // ============================================================================================
    @Operation(summary = "Get all books (paginated)")
    @GetMapping("/books")
    @PreAuthorize("hasAnyAuthority('READER', 'AUTHOR', 'LIBRARIAN', 'ADMIN', 'ADMIN_LIBRARIAN')")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getAllBooks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<BookResponseDto> books = bookService.getAllBooksPaginated(page, size);

        return ResponseUtils.success(books, ApiMessageKey.READER_BOOKS_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // SEARCH BOOKS
    // ============================================================================================
    @Operation(summary = "Search books")
    @PostMapping("/books/search")
    @PreAuthorize("hasAnyAuthority('READER', 'AUTHOR', 'LIBRARIAN', 'ADMIN', 'ADMIN_LIBRARIAN')")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> searchBooks(@Valid @RequestBody BookSearchRequestDto requestDto) {
        Pageable pageable = PageRequest.of(requestDto.page(), requestDto.size());

        PageResponse<BookResponseDto> result = bookService.searchBooks(requestDto, pageable);

        return ResponseUtils.success(result, ApiMessageKey.READER_SEARCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET BOOK BY ID
    // ============================================================================================
    @Operation(summary = "Get book by ID")
    @GetMapping("/books/{id}")
    @PreAuthorize("hasAnyAuthority('READER', 'AUTHOR', 'LIBRARIAN', 'ADMIN', 'ADMIN_LIBRARIAN')")
    public ResponseEntity<ApiResponse<BookResponseDto>> getBookById(@PathVariable Long id) {
        BookResponseDto book = bookService.getBookById(id);

        return ResponseUtils.success(book, ApiMessageKey.READER_BOOK_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET SIMILAR BOOKS
    // ============================================================================================
    @Operation(summary = "Get similar books")
    @GetMapping("/books/{bookId}/similar")
    @PreAuthorize("hasAnyAuthority('READER', 'AUTHOR', 'LIBRARIAN', 'ADMIN', 'ADMIN_LIBRARIAN')")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getSimilarBooks(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size
    ) {
        PageResponse<BookResponseDto> result = similarityService.getSmartSimilarBooks(bookId, page, size);

        return ResponseUtils.success(result, ApiMessageKey.READER_SIMILAR_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET BOOK COVERS (PUBLIC - NO AUTH REQUIRED)
    // ============================================================================================
    @Operation(summary = "Get book covers with titles (public endpoint)")
    @GetMapping("/covers")
    public ResponseEntity<ApiResponse<PageResponse<BookCoverResponse>>> getBookCovers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "18") int size
    ) {
        PageResponse<BookCoverResponse> covers = bookService.getBookCovers(page, size);

        return ResponseUtils.success(covers, ApiMessageKey.READER_BOOKS_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
