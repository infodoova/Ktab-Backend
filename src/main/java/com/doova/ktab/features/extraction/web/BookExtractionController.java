package com.doova.ktab.features.extraction.web;

import com.doova.ktab.features.extraction.BookExtractionService;
import com.doova.ktab.features.extraction.dto.BookExtractionResult;
import com.doova.ktab.features.extraction.pdf.PdfRejectedException;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books")
public class BookExtractionController {

    private final BookExtractionService service;

    @Operation(summary = "Extract structured text (metadata, TOC, chapters) from a digital Arabic PDF; nothing is saved")
    @PostMapping(path = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LIBRARIAN', 'ADMIN_LIBRARIAN')")
    public BookExtractionResult extract(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "includePages", defaultValue = "false") boolean includePages)
            throws IOException {
        BookExtractionResult result = service.extract(file.getBytes());
        return includePages ? result : result.compact();
    }

    @ExceptionHandler(PdfRejectedException.class)
    public ResponseEntity<Map<String, String>> rejected(PdfRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("reason", e.getReason().name(), "message", e.getMessage()));
    }
}
