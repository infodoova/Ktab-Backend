package com.doova.ktab.controller.v1.admin;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.library.AssignLibrarianRequest;
import com.doova.ktab.dto.library.CreateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.UpdateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.LibraryOrganizationResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.service.library.LibraryOrganizationService;
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
@Tag(name = "Admin Library Management API", description = "Endpoints for managing institutional library organizations and assigning librarian staff.")
public class AdminLibraryOrganizationController {

    private final LibraryOrganizationService libraryOrgService;
    private final MessageSource messageSource;

    @Operation(summary = "Create a new library organization")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<ApiResponse<LibraryOrganizationResponseDto>> createLibrary(
            @Valid @RequestBody CreateLibraryOrganizationRequest req
    ) {
        LibraryOrganizationResponseDto created = libraryOrgService.createOrganization(req);
        return ResponseUtils.success(created, ApiMessageKey.LIBRARY_ORGANIZATION_CREATED_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    @Operation(summary = "Update an existing library organization")
    @PatchMapping(path = "/{id}", consumes = "application/json")
    public ResponseEntity<ApiResponse<LibraryOrganizationResponseDto>> updateLibrary(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLibraryOrganizationRequest req
    ) {
        LibraryOrganizationResponseDto updated = libraryOrgService.updateOrganization(id, req);
        return ResponseUtils.success(updated, ApiMessageKey.LIBRARY_ORGANIZATION_UPDATED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Assign or create a librarian for a library organization")
    @PostMapping(path = "/{id}/librarians", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> assignLibrarian(
            @PathVariable Long id,
            @Valid @RequestBody AssignLibrarianRequest req
    ) {
        libraryOrgService.assignLibrarian(id, req);
        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_STAFF_ASSIGNED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Remove a librarian from a library organization")
    @DeleteMapping(path = "/{id}/librarians/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeLibrarian(
            @PathVariable Long id,
            @PathVariable Long userId
    ) {
        libraryOrgService.removeLibrarian(id, userId);
        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_STAFF_REMOVED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
