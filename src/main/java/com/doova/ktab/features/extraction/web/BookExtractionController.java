package com.doova.ktab.features.extraction.web;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.book.BookNavigatorResponse;
import com.doova.ktab.dto.book.ReaderPageResponse;
import com.doova.ktab.features.extraction.BookExtractionQueryService;
import com.doova.ktab.features.extraction.BookExtractionService;
import com.doova.ktab.features.extraction.dto.BookExtractionResult;
import com.doova.ktab.features.extraction.pdf.PdfRejectedException;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/api/books", "/api/v1/books"})
public class BookExtractionController {

    private final BookExtractionService service;
    private final BookExtractionQueryService queryService;

    @Operation(summary = "Extract structured text (metadata, TOC, chapters) from a digital Arabic PDF; nothing is saved")
    @PostMapping(path = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LIBRARIAN', 'ADMIN_LIBRARIAN')")
    public BookExtractionResult extract(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "includePages", defaultValue = "false") boolean includePages)
            throws IOException {
        BookExtractionResult result = service.extract(file.getBytes());
        return includePages ? result : result.compact();
    }

    @Operation(summary = "Get paginated reader text starting with 100% accuracy from the book's true start, excluding appendixes")
    @GetMapping(path = {"/{bookId}/pages", "/{bookId}/reader-pages", "/{bookId}/extract/pages"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<ReaderPageResponse>> getReaderPage(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "80") @Min(1) int wordsPerPage
    ) {
        ReaderPageResponse response = queryService.getReaderPage(bookId, page, wordsPerPage);
        return ResponseUtils.success(response, "تم جلب صفحة الكتاب بنجاح", HttpStatus.OK);
    }

    @Operation(summary = "Locate a cited passage on the current reader pages")
    @GetMapping(path = "/{bookId}/reader-locate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> locateReaderSnippet(
            @PathVariable Long bookId,
            @RequestParam String snippet,
            @RequestParam(defaultValue = "80") @Min(1) int wordsPerPage
    ) {
        return ResponseUtils.success(queryService.locateReaderSnippet(bookId, snippet, wordsPerPage),
                "تم تحديد موضع الاقتباس", HttpStatus.OK);
    }

    @Operation(summary = "Map a cited PDF page to the current reader pagination")
    @GetMapping(path = "/{bookId}/reader-pdf-page")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> locateReaderPdfPage(
            @PathVariable Long bookId,
            @RequestParam @Min(1) int pdfPageNumber,
            @RequestParam(defaultValue = "80") @Min(1) int wordsPerPage
    ) {
        return ResponseUtils.success(queryService.locateReaderPdfPage(bookId, pdfPageNumber, wordsPerPage),
                "تم تحديد صفحة الكتاب", HttpStatus.OK);
    }

    @Operation(summary = "Get book navigation hierarchy (chapters, parts) with direct target page mapping, and separate appendixes")
    @GetMapping(path = {"/{bookId}/navigator", "/{bookId}/navigation"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<BookNavigatorResponse>> getNavigator(
            @PathVariable Long bookId,
            @RequestParam(defaultValue = "80") @Min(1) int wordsPerPage
    ) {
        BookNavigatorResponse response = queryService.getNavigator(bookId, wordsPerPage);
        return ResponseUtils.success(response, "تم جلب شجرة تصفح الكتاب بنجاح", HttpStatus.OK);
    }

    @Operation(summary = "Get full section content by section ID (for appendixes, glossaries, bibliographies)")
    @GetMapping(path = {"/{bookId}/sections/{sectionId}", "/{bookId}/extract/sections/{sectionId}"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<com.doova.ktab.dto.book.BookSectionContentResponse>> getSectionContent(
            @PathVariable Long bookId,
            @PathVariable Long sectionId
    ) {
        com.doova.ktab.dto.book.BookSectionContentResponse response = queryService.getSectionContent(bookId, sectionId);
        return ResponseUtils.success(response, "تم جلب محتوى الملحق بنجاح", HttpStatus.OK);
    }

    @ExceptionHandler(PdfRejectedException.class)
    public ResponseEntity<Map<String, String>> rejected(PdfRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("reason", e.getReason().name(), "message", e.getMessage()));
    }
}
