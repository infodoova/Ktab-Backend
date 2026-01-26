package com.doova.ktab.controller.v1.reader;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.response.BookStatsResponse;
import com.doova.ktab.dto.response.TextRangeResponse;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.service.interfaces.book.BookTextService;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/reader", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Reader Book Text API", description = "Endpoints for reading, paginating, and analyzing book text.")
public class BookTextController {

    private final BookTextService bookTextService;
    private final MessageSource messageSource;

    // ============================================================================================
    // GET FULL BOOK TEXT
    // ============================================================================================
    @Operation(summary = "Get full book text")
    @GetMapping("/books/{bookId}/text")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> getFullText(@PathVariable Long bookId) {

        String text = bookTextService.getFullText(bookId);

        return ResponseUtils.success(text, ApiMessageKey.BOOK_TEXT_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET TEXT BY CHARACTER RANGE
    // ============================================================================================
    @Operation(summary = "Get book text by character range")
    @GetMapping("/books/{bookId}/text/range")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<TextRangeResponse>> getTextRange(@PathVariable Long bookId, @RequestParam int start, @RequestParam int end) {

        TextRangeResponse result = bookTextService.getTextByCharacterRange(bookId, start, end);

        return ResponseUtils.success(result, ApiMessageKey.BOOK_TEXT_RANGE_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET TEXT BY PAGE RANGE
    // ============================================================================================
    @Operation(summary = "Get book text by page range")
    @GetMapping("/books/{bookId}/text/pages")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<String>> getTextByPages(@PathVariable Long bookId, @RequestParam int from, @RequestParam int to) {

        String text = bookTextService.getTextByPageRange(bookId, from, to);

        return ResponseUtils.success(text, ApiMessageKey.BOOK_TEXT_PAGE_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET BOOK TEXT STATS
    // ============================================================================================
    @Operation(summary = "Get book text statistics")
    @GetMapping("/books/{bookId}/text/stats")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<BookStatsResponse>> getStats(@PathVariable Long bookId) {

        BookStatsResponse stats = bookTextService.getBookStats(bookId);

        return ResponseUtils.success(stats, ApiMessageKey.BOOK_TEXT_STATS_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    // ============================================================================================
    // GET TEXT BY WORD RANGE
    // ============================================================================================
    @Operation(summary = "Get book text by word range")
    @GetMapping("/books/{bookId}/text/words")
    @PreAuthorize("hasAnyAuthority('READER')")
    public ResponseEntity<ApiResponse<TextRangeResponse>> getTextByWordRange(@PathVariable Long bookId, @RequestParam int start, @RequestParam int end) {

        TextRangeResponse result = bookTextService.getTextByWordRange(bookId, start, end);

        return ResponseUtils.success(result, ApiMessageKey.BOOK_TEXT_RANGE_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
