package com.doova.ktab.features.ocr.web;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.utils.response.ResponseUtils;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
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
@ApiVersion(value = 1, keepLegacyPath = true)
@RequestMapping(path = "/books", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Book Structure API", description = "Endpoints for book section tree structure and scanning settings.")
public class BookStructureController {

    private final BookStructureService structureService;
    private final MessageSource messageSource;

    @Operation(summary = "Get the hierarchical section tree for a book")
    @GetMapping("/{bookId}/structure")
    public ResponseEntity<ApiResponse<BookStructureResponseDto>> getStructure(@PathVariable Long bookId) {
        return ResponseUtils.success(structureService.getStructure(bookId),
                ApiMessageKey.BOOK_STRUCTURE_FETCHED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Save manual corrections to book structure and trigger page reassignment")
    @PutMapping("/{bookId}/structure")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<BookStructureResponseDto>> updateStructure(
            @PathVariable Long bookId,
            @Valid @RequestBody BookStructureUpdateRequestDto request
    ) {
        return ResponseUtils.success(structureService.updateStructure(bookId, request),
                ApiMessageKey.BOOK_STRUCTURE_UPDATED.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Update book scanning settings (reading direction)")
    @PatchMapping("/{bookId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ADMIN_LIBRARIAN', 'LIBRARIAN')")
    public ResponseEntity<ApiResponse<Void>> patchSettings(
            @PathVariable Long bookId,
            @Valid @RequestBody BookSettingsPatchRequestDto request
    ) {
        structureService.patchSettings(bookId, request);
        return ResponseUtils.success(null, ApiMessageKey.BOOK_SETTINGS_UPDATED.getMessage(messageSource), HttpStatus.OK);
    }
}
