package com.doova.ktab.service.library;

import com.doova.ktab.dto.library.AssignLibrarianRequest;
import com.doova.ktab.dto.library.CreateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.UpdateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.LibrarianStaffResponseDto;
import com.doova.ktab.dto.library.LibraryOrganizationResponseDto;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.pagination.PageResponse;

import java.util.List;

public interface LibraryOrganizationService {

    LibraryOrganizationResponseDto createOrganization(CreateLibraryOrganizationRequest req);

    LibraryOrganizationResponseDto updateOrganization(Long id, UpdateLibraryOrganizationRequest req);

    LibraryOrganizationResponseDto getOrganizationById(Long id);

    LibraryOrganizationResponseDto getOrganizationBySlug(String slug);

    PageResponse<LibraryOrganizationResponseDto> getAllActiveOrganizations(int page, int size, String search);

    void assignLibrarian(Long organizationId, AssignLibrarianRequest req);

    void removeLibrarian(Long organizationId, Long userId);

    LibraryOrganization requireAdminLibrarianOrganization(User user);

    LibraryOrganizationResponseDto getMyOrganization(User adminLibrarian);

    LibraryOrganizationResponseDto updateMyOrganizationProfile(User adminLibrarian, UpdateLibraryOrganizationRequest req);

    List<LibrarianStaffResponseDto> getStaffMembers(User adminLibrarian);

    void assignStaffByAdminLibrarian(User adminLibrarian, AssignLibrarianRequest req);

    void removeStaffByAdminLibrarian(User adminLibrarian, Long targetUserId);

    LibraryOrganizationResponseDto toDto(LibraryOrganization org);
}
