package com.doova.ktab.features.ingestion.routing;

import com.doova.ktab.enums.book.IngestionRoute;
import com.doova.ktab.features.ingestion.pdf.PdfClassificationResult;
import com.doova.ktab.model.attachment.Attachment;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.service.file.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.Optional;

/**
 * Escape hatches for the ingestion router (docs/ocr_engine_v3.md, Phase 2.4) — the safety
 * valve for when a book classifies wrong in production, without touching content or jobs.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ingestion")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN')")
@Tag(name = "Ingestion Routing API", description = "Endpoints for inspecting book ingestion routes and classification status.")
public class IngestionController {

    private final IngestionRouter router;
    private final AttachmentService attachmentService;
    private final BookRepository bookRepository;

    /**
     * Routing status for support/admin tooling, so a misrouted book can be understood
     * without a direct DB query. See docs/ocr_engine_v3.md, Phase 2.4.
     */
    @GetMapping("/books/{bookId}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + bookId));

        // Map.of() rejects null values, and pdfType/ingestionRoute/classifierVersion are all
        // null for a book that hasn't been classified yet - use a mutable map instead.
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("bookId", bookId);
        status.put("pdfType", book.getPdfType());
        status.put("ingestionRoute", book.getIngestionRoute());
        status.put("ingestionRouteLocked", book.isIngestionRouteLocked());
        status.put("classifierVersion", book.getClassifierVersion());
        status.put("ocrStatus", book.getOcrStatus());
        return ResponseEntity.ok(status);
    }

    /**
     * Re-run classification only. Returns the evidence; does not purge content or launch a job.
     */
    @PostMapping("/books/{bookId}/classify")
    public ResponseEntity<Map<String, Object>> classify(@PathVariable Long bookId) {
        String pdfKey = requirePdfKey(bookId);
        PdfClassificationResult result = router.classifyOnly(bookId, pdfKey);
        return ResponseEntity.ok(Map.of("bookId", bookId, "classification", result));
    }

    /**
     * Admin override: pins the book to the given route, locked against future reclassification.
     */
    @PostMapping("/books/{bookId}/route")
    public ResponseEntity<Map<String, Object>> overrideRoute(
            @PathVariable Long bookId,
            @RequestParam IngestionRoute route
    ) {
        router.overrideRoute(bookId, route);
        return ResponseEntity.ok(Map.of("bookId", bookId, "route", route, "locked", true));
    }

    private String requirePdfKey(Long bookId) {
        Optional<Attachment> attachment = attachmentService.getAttachment(bookId, "Book", "PDF_SOURCE");
        if (attachment.isEmpty()) {
            throw new IllegalArgumentException("No PDF_SOURCE attachment found for bookId " + bookId);
        }
        return attachment.get().getStoragePath();
    }
}
