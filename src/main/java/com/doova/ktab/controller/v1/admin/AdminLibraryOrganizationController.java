package com.doova.ktab.controller.v1.admin;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.library.AssignLibrarianRequest;
import com.doova.ktab.dto.library.CreateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.UpdateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.LibraryOrganizationResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.library.LibraryOrganizationService;
import com.doova.ktab.utils.pagination.PageResponse;
import com.doova.ktab.utils.response.ResponseUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/admin/libraries", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN')")
@Tag(name = "Admin Library Management API", description = "Endpoints for managing institutional library organizations.")
public class AdminLibraryOrganizationController {

    private final LibraryOrganizationService libraryOrgService;
    private final MessageSource messageSource;

    @Operation(summary = "Get all library organizations with their administrators (paginated)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<LibraryOrganizationResponseDto>>> getAllLibraries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        PageResponse<LibraryOrganizationResponseDto> result = libraryOrgService.getAllOrganizationsForAdmin(page, size,
                search);
        return ResponseUtils.success(result, ApiMessageKey.LIBRARY_ORGANIZATION_FETCH_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }

    @Operation(summary = "Create a new library organization")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<LibraryOrganizationResponseDto>> createLibrary(
            @Valid @RequestBody CreateLibraryOrganizationRequest req) {
        LibraryOrganizationResponseDto created = libraryOrgService.createOrganization(req);
        return ResponseUtils.success(created,
                ApiMessageKey.LIBRARY_ORGANIZATION_CREATED_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    @Operation(summary = "Update an existing library organization")
    @PatchMapping(path = "/{id}", consumes = "application/json")
    public ResponseEntity<ApiResponse<LibraryOrganizationResponseDto>> updateLibrary(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLibraryOrganizationRequest req) {
        LibraryOrganizationResponseDto updated = libraryOrgService.updateOrganization(id, req);
        return ResponseUtils.success(updated,
                ApiMessageKey.LIBRARY_ORGANIZATION_UPDATED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Delete a library organization along with its admin, staff, and books")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteLibrary(
            @PathVariable Long id) {
        libraryOrgService.deleteOrganization(id);
        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_ORGANIZATION_DELETED_SUCCESS.getMessage(messageSource),
                HttpStatus.OK);
    }
}
