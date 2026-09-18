package com.doova.ktab.controller.v1.reader;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.library.LibraryOrganizationResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.librarian.LibrarianBookService;
import com.doova.ktab.service.library.LibraryOrganizationService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/reader/organizations", produces = "application/json")
@RequiredArgsConstructor
@Tag(name = "Public Library Directory API", description = "Public endpoints for readers to browse library organizations and their published catalogs.")
public class PublicLibraryController {

    private final LibraryOrganizationService libraryOrgService;
    private final LibrarianBookService librarianBookService;
    private final MessageSource messageSource;

    @Operation(summary = "Browse active library organizations")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LibraryOrganizationResponseDto>>> getOrganizations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search
    ) {
        PageResponse<LibraryOrganizationResponseDto> orgs = libraryOrgService.getAllActiveOrganizations(page, size, search);
        return ResponseUtils.success(orgs, ApiMessageKey.LIBRARY_ORGANIZATION_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Search active library organizations directory")
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<LibraryOrganizationResponseDto>>> searchOrganizations(
            @jakarta.validation.Valid @RequestBody com.doova.ktab.dto.library.LibraryOrganizationSearchRequest requestDto
    ) {
        String sortProperty = requestDto.sortBy() != null ? requestDto.sortBy() : "name";
        org.springframework.data.domain.Sort.Direction direction = requestDto.sortDirection() != null ? requestDto.sortDirection() : org.springframework.data.domain.Sort.Direction.ASC;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(requestDto.page(), requestDto.size(), org.springframework.data.domain.Sort.by(direction, sortProperty));

        PageResponse<LibraryOrganizationResponseDto> orgs = libraryOrgService.searchOrganizations(requestDto, pageable);
        return ResponseUtils.success(orgs, ApiMessageKey.LIBRARY_ORGANIZATION_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get library organization profile by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LibraryOrganizationResponseDto>> getOrganizationById(@PathVariable Long id) {
        LibraryOrganizationResponseDto org = libraryOrgService.getOrganizationById(id);
        return ResponseUtils.success(org, ApiMessageKey.LIBRARY_ORGANIZATION_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Get published catalog of a specific library organization")
    @GetMapping("/{id}/books")
    public ResponseEntity<ApiResponse<PageResponse<BookResponseDto>>> getLibraryBooks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PageResponse<BookResponseDto> books = librarianBookService.getPublishedBooksByLibrary(id, page, size);
        return ResponseUtils.success(books, ApiMessageKey.LIBRARIAN_BOOKS_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
