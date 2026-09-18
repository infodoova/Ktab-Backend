package com.doova.ktab.service.library.impl;

import com.doova.ktab.dto.library.AssignLibrarianRequest;
import com.doova.ktab.dto.library.CreateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.UpdateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.LibrarianStaffResponseDto;
import com.doova.ktab.dto.library.LibraryOrganizationResponseDto;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.status.Status;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.library.LibraryOrganizationRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.library.LibraryOrganizationService;
import com.doova.ktab.utils.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryOrganizationServiceImpl implements LibraryOrganizationService {

    private final LibraryOrganizationRepository libraryOrgRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final PasswordEncoder passwordEncoder;

    // =========================================================================
    // CREATE ORGANIZATION (Admin)
    // =========================================================================
    @Override
    @Transactional
    public LibraryOrganizationResponseDto createOrganization(CreateLibraryOrganizationRequest req) {
        String slug = generateSlug(req.name());

        LibraryOrganization org = LibraryOrganization.builder()
                .name(req.name())
                .slug(slug)
                .description(req.description())
                .city(req.city())
                .country(req.country())
                .address(req.address())
                .website(req.website())
                .email(req.email())
                .phone(req.phone())
                .status(Status.ACTIVE.getCode())
                .build();

        LibraryOrganization saved = libraryOrgRepository.save(org);
        log.info("Created library organization: {} with slug: {}", saved.getName(), saved.getSlug());
        return toDto(saved);
    }

    // =========================================================================
    // UPDATE ORGANIZATION (Admin)
    // =========================================================================
    @Override
    @Transactional
    public LibraryOrganizationResponseDto updateOrganization(Long id, UpdateLibraryOrganizationRequest req) {
        LibraryOrganization org = libraryOrgRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_FOUND));

        if (req.name() != null && !req.name().isBlank()) {
            org.setName(req.name());
        }
        if (req.description() != null) org.setDescription(req.description());
        if (req.city() != null) org.setCity(req.city());
        if (req.country() != null) org.setCountry(req.country());
        if (req.address() != null) org.setAddress(req.address());
        if (req.website() != null) org.setWebsite(req.website());
        if (req.email() != null) org.setEmail(req.email());
        if (req.phone() != null) org.setPhone(req.phone());
        if (req.status() != null && !req.status().isBlank()) org.setStatus(req.status().toUpperCase());

        LibraryOrganization updated = libraryOrgRepository.save(org);
        return toDto(updated);
    }

    // =========================================================================
    // GET BY ID / SLUG
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public LibraryOrganizationResponseDto getOrganizationById(Long id) {
        LibraryOrganization org = libraryOrgRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_FOUND));
        return toDto(org);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryOrganizationResponseDto getOrganizationBySlug(String slug) {
        LibraryOrganization org = libraryOrgRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_FOUND));
        return toDto(org);
    }

    // =========================================================================
    // GET ALL ACTIVE ORGANIZATIONS (Public reader directory)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<LibraryOrganizationResponseDto> getAllActiveOrganizations(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<LibraryOrganization> pageResult;
        if (search != null && !search.isBlank()) {
            pageResult = libraryOrgRepository.findByNameContainingIgnoreCaseAndStatus(search.trim(), Status.ACTIVE.getCode(), pageable);
        } else {
            pageResult = libraryOrgRepository.findAllByStatus(Status.ACTIVE.getCode(), pageable);
        }

        return PageResponse.fromPage(pageResult.map(this::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LibraryOrganizationResponseDto> searchOrganizations(
            com.doova.ktab.dto.library.LibraryOrganizationSearchRequest req,
            Pageable pageable
    ) {
        Page<LibraryOrganization> pageResult = libraryOrgRepository.findAll(
                com.doova.ktab.specification.LibraryOrganizationSpecification.forActiveDirectory(req),
                pageable
        );
        return PageResponse.fromPage(pageResult.map(this::toDto));
    }

    // =========================================================================
    // ASSIGN / CREATE LIBRARIAN STAFF (Admin / Admin Librarian)
    // =========================================================================
    @Override
    @Transactional
    public void assignLibrarian(Long organizationId, AssignLibrarianRequest req) {
        LibraryOrganization org = libraryOrgRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_FOUND));

        UserRole targetRole = UserRole.LIBRARIAN;
        if (req.role() != null && !req.role().isBlank()) {
            UserRole requested = UserRole.fromCode(req.role());
            if (requested == UserRole.ADMIN_LIBRARIAN) {
                targetRole = UserRole.ADMIN_LIBRARIAN;
            }
        }

        final UserRole finalRole = targetRole;

        User user = userRepository.findByEmail(req.email()).orElseGet(() -> {
            log.info("Creating new library staff user: {} with role {}", req.email(), finalRole.name());
            User newUser = User.builder()
                    .email(req.email())
                    .firstName(req.firstName())
                    .middleName(req.middleName())
                    .lastName(req.lastName())
                    .role(finalRole.getCode())
                    .active(Status.ACTIVE.getCode())
                    .build();

            newUser.setPasswordAndDigest(req.password(), passwordEncoder);
            return newUser;
        });

        user.setRole(targetRole.getCode());
        user.setActive(Status.ACTIVE.getCode());
        user.setLibraryOrganization(org);

        userRepository.save(user);
        log.info("Assigned user {} as {} to organization {}", user.getEmail(), targetRole.name(), org.getName());
    }

    // =========================================================================
    // REMOVE LIBRARIAN STAFF (Admin / Admin Librarian)
    // =========================================================================
    @Override
    @Transactional
    public void removeLibrarian(Long organizationId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.USER_NOT_FOUND));

        if (user.getLibraryOrganization() != null && user.getLibraryOrganization().getId().equals(organizationId)) {
            user.setLibraryOrganization(null);
            user.setRole(UserRole.READER.getCode()); // safe fallback to reader
            userRepository.save(user);
            log.info("Removed librarian {} from organization {}", user.getEmail(), organizationId);
        }
    }

    // =========================================================================
    // ADMIN LIBRARIAN ACTIONS (Scoped to caller's library)
    // =========================================================================
    @Override
    public LibraryOrganization requireAdminLibrarianOrganization(User user) {
        if (user == null || user.getId() == null) {
            log.warn("Access denied: User has no associated library organization");
            throw new BadRequestException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_ASSOCIATED);
        }
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.USER_NOT_FOUND));
        if (managed.getLibraryOrganization() == null) {
            log.warn("Access denied: User {} has no associated library organization", managed.getEmail());
            throw new BadRequestException(ApiMessageKey.LIBRARY_ORGANIZATION_NOT_ASSOCIATED);
        }
        return managed.getLibraryOrganization();
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryOrganizationResponseDto getMyOrganization(User adminLibrarian) {
        LibraryOrganization org = requireAdminLibrarianOrganization(adminLibrarian);
        return toDto(org);
    }

    @Override
    @Transactional
    public LibraryOrganizationResponseDto updateMyOrganizationProfile(User adminLibrarian, UpdateLibraryOrganizationRequest req) {
        LibraryOrganization org = requireAdminLibrarianOrganization(adminLibrarian);
        return updateOrganization(org.getId(), req);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LibrarianStaffResponseDto> getStaffMembers(User adminLibrarian) {
        LibraryOrganization org = requireAdminLibrarianOrganization(adminLibrarian);
        return userRepository.findAllByLibraryOrganizationId(org.getId()).stream()
                .map(this::toStaffDto)
                .toList();
    }

    @Override
    @Transactional
    public void assignStaffByAdminLibrarian(User adminLibrarian, AssignLibrarianRequest req) {
        LibraryOrganization org = requireAdminLibrarianOrganization(adminLibrarian);
        assignLibrarian(org.getId(), req);
    }

    @Override
    @Transactional
    public void removeStaffByAdminLibrarian(User adminLibrarian, Long targetUserId) {
        LibraryOrganization org = requireAdminLibrarianOrganization(adminLibrarian);
        if (adminLibrarian.getId().equals(targetUserId)) {
            throw new BadRequestException(ApiMessageKey.LIBRARY_STAFF_CANNOT_REMOVE_SELF);
        }
        removeLibrarian(org.getId(), targetUserId);
    }

    private LibrarianStaffResponseDto toStaffDto(User user) {
        UserRole role = UserRole.fromCode(user.getRole());
        return LibrarianStaffResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(role != null ? role.name() : user.getRole())
                .roleCode(user.getRole())
                .active(user.getActive())
                .libraryOrganizationId(user.getLibraryOrganization() != null ? user.getLibraryOrganization().getId() : null)
                .libraryOrganizationName(user.getLibraryOrganization() != null ? user.getLibraryOrganization().getName() : null)
                .build();
    }

    // =========================================================================
    // HELPER: SLUG & DTO MAPPING
    // =========================================================================
    private String generateSlug(String name) {
        if (name == null || name.isBlank()) {
            return "lib-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String base = name.trim().toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\u0600-\\u06FF]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "lib";
        }
        String slug = base;
        int count = 1;
        while (libraryOrgRepository.existsBySlug(slug)) {
            slug = base + "-" + count++;
        }
        return slug;
    }

    @Override
    public LibraryOrganizationResponseDto toDto(LibraryOrganization org) {
        long totalBooks = bookRepository.countByLibraryOrganizationId(org.getId());
        long totalStaff = userRepository.countByLibraryOrganizationId(org.getId());

        return LibraryOrganizationResponseDto.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .description(org.getDescription())
                .city(org.getCity())
                .country(org.getCountry())
                .address(org.getAddress())
                .website(org.getWebsite())
                .email(org.getEmail())
                .phone(org.getPhone())
                .status(org.getStatus())
                .totalBooks(totalBooks)
                .totalStaff(totalStaff)
                .createdAt(org.getCreatedAt())
                .build();
    }
}
