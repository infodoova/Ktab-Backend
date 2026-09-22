package com.doova.ktab.features.ocr.web;

import com.doova.ktab.dto.book.BookSettingsPatchRequestDto;
import com.doova.ktab.dto.book.BookStructureResponseDto;
import com.doova.ktab.dto.book.BookStructureUpdateRequestDto;
import com.doova.ktab.service.book.BookStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Tag(name = "Book Structure API", description = "Endpoints for book section tree structure and scanning settings")
public class BookStructureController {

    private final BookStructureService structureService;

    @Operation(summary = "Get the hierarchical section tree for a book")
    @GetMapping("/{bookId}/structure")
    public ResponseEntity<BookStructureResponseDto> getStructure(@PathVariable Long bookId) {
        return ResponseEntity.ok(structureService.getStructure(bookId));
    }

    @Operation(summary = "Save manual corrections to book structure and trigger page reassignment")
    @PutMapping("/{bookId}/structure")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN', 'LIBRARIAN')")
    public ResponseEntity<BookStructureResponseDto> updateStructure(
            @PathVariable Long bookId,
            @Valid @RequestBody BookStructureUpdateRequestDto request
    ) {
        return ResponseEntity.ok(structureService.updateStructure(bookId, request));
    }

    @Operation(summary = "Update book scanning settings (reading direction)")
    @PatchMapping("/{bookId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN', 'LIBRARIAN')")
    public ResponseEntity<Void> patchSettings(
            @PathVariable Long bookId,
            @Valid @RequestBody BookSettingsPatchRequestDto request
    ) {
        structureService.patchSettings(bookId, request);
        return ResponseEntity.noContent().build();
    }
}
