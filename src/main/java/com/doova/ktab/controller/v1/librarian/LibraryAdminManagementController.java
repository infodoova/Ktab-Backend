package com.doova.ktab.controller.v1.librarian;

import com.doova.ktab.annotation.ApiVersion;
import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.dto.library.AssignLibrarianRequest;
import com.doova.ktab.dto.library.UpdateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.LibrarianStaffResponseDto;
import com.doova.ktab.dto.library.LibraryOrganizationResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.model.user.User;
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

import java.util.List;

@ApiVersion(1)
@RestController
@RequestMapping(path = "/library-admin", produces = "application/json")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN_LIBRARIAN', 'ADMIN')")
@Tag(name = "Library Organization Admin API", description = "Endpoints for library organization administrators (ADMIN_LIBRARIAN) to manage their organization profile and staff members.")
public class LibraryAdminManagementController {

    private final LibraryOrganizationService libraryOrgService;
    private final MessageSource messageSource;

    @Operation(summary = "Get current administrator's library organization profile")
    @GetMapping("/organization")
    public ResponseEntity<ApiResponse<LibraryOrganizationResponseDto>> getMyOrganization(
            @CurrentUser User adminLibrarian
    ) {
        LibraryOrganizationResponseDto dto = libraryOrgService.getMyOrganization(adminLibrarian);
        return ResponseUtils.success(dto, ApiMessageKey.LIBRARY_ORGANIZATION_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Update current administrator's library organization profile")
    @PatchMapping(path = "/organization", consumes = "application/json")
    public ResponseEntity<ApiResponse<LibraryOrganizationResponseDto>> updateMyOrganization(
            @CurrentUser User adminLibrarian,
            @Valid @RequestBody UpdateLibraryOrganizationRequest req
    ) {
        LibraryOrganizationResponseDto updated = libraryOrgService.updateMyOrganizationProfile(adminLibrarian, req);
        return ResponseUtils.success(updated, ApiMessageKey.LIBRARY_ORGANIZATION_UPDATED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "List all staff members belonging to the library organization")
    @GetMapping("/staff")
    public ResponseEntity<ApiResponse<List<LibrarianStaffResponseDto>>> getStaff(
            @CurrentUser User adminLibrarian
    ) {
        List<LibrarianStaffResponseDto> staff = libraryOrgService.getStaffMembers(adminLibrarian);
        return ResponseUtils.success(staff, ApiMessageKey.LIBRARY_STAFF_FETCH_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }

    @Operation(summary = "Invite or assign a new staff member to the library organization")
    @PostMapping(path = "/staff", consumes = "application/json")
    public ResponseEntity<ApiResponse<Void>> addStaff(
            @CurrentUser User adminLibrarian,
            @Valid @RequestBody AssignLibrarianRequest req
    ) {
        libraryOrgService.assignStaffByAdminLibrarian(adminLibrarian, req);
        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_STAFF_ASSIGNED_SUCCESS.getMessage(messageSource), HttpStatus.CREATED);
    }

    @Operation(summary = "Remove a staff member from the library organization")
    @DeleteMapping(path = "/staff/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeStaff(
            @CurrentUser User adminLibrarian,
            @PathVariable Long userId
    ) {
        libraryOrgService.removeStaffByAdminLibrarian(adminLibrarian, userId);
        return ResponseUtils.success(null, ApiMessageKey.LIBRARY_STAFF_REMOVED_SUCCESS.getMessage(messageSource), HttpStatus.OK);
    }
}
