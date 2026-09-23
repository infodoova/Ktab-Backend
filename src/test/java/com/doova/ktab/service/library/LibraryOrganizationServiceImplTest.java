package com.doova.ktab.service.library;

import com.doova.ktab.dto.library.AssignLibrarianRequest;
import com.doova.ktab.dto.library.CreateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.LibraryOrganizationResponseDto;
import com.doova.ktab.dto.library.LibrarianStaffResponseDto;
import com.doova.ktab.dto.library.UpdateLibrarianStaffRequest;
import com.doova.ktab.dto.library.UpdateLibraryAdminRequest;
import com.doova.ktab.dto.library.UpdateLibraryOrganizationRequest;
import com.doova.ktab.enums.book.BookSource;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.enums.status.Status;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.library.LibraryOrganization;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.library.LibraryOrganizationRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.book.BookFileService;
import com.doova.ktab.service.library.impl.LibraryOrganizationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryOrganizationServiceImplTest {

    @Mock
    private LibraryOrganizationRepository libraryOrgRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private BookFileService bookFileService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private LibraryOrganizationServiceImpl service;

    private LibraryOrganization org;
    private User adminUser;

    @BeforeEach
    void setUp() {
        org = LibraryOrganization.builder()
                .name("Alexandria Library")
                .slug("alexandria-library")
                .city("Alexandria")
                .country("Egypt")
                .status(Status.ACTIVE.getCode())
                .build();
        org.setId(1L);

        adminUser = User.builder()
                .email("admin@alexlib.org")
                .firstName("Ahmed")
                .lastName("Hassan")
                .role(UserRole.ADMIN_LIBRARIAN.getCode())
                .active(Status.ACTIVE.getCode())
                .libraryOrganization(org)
                .build();
        adminUser.setId(100L);
    }

    @Test
    @DisplayName("createOrganization_withAdmin_createsLibraryAndAdminLibrarianInOneRequest")
    void createOrganization_withAdmin_createsLibraryAndAdminLibrarianInOneRequest() {
        AssignLibrarianRequest adminReq = new AssignLibrarianRequest(
                "admin@alexlib.org", "Ahmed", null, "Hassan", "Secret123!"
        );
        CreateLibraryOrganizationRequest req = new CreateLibraryOrganizationRequest(
                "Alexandria Library", "Great Library", "Alexandria", "Egypt", "Corniche", "https://bibalex.org", "info@bibalex.org", "+2030000000", adminReq
        );

        when(libraryOrgRepository.existsBySlug(anyString())).thenReturn(false);
        when(libraryOrgRepository.save(any(LibraryOrganization.class))).thenAnswer(invocation -> {
            LibraryOrganization saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(libraryOrgRepository.findById(1L)).thenReturn(Optional.of(org));
        when(userRepository.existsByEmail("admin@alexlib.org")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("hashed-pwd");
        when(userRepository.findAllByLibraryOrganizationId(1L)).thenReturn(List.of(adminUser));
        when(bookRepository.countByLibraryOrganizationId(1L)).thenReturn(0L);
        when(userRepository.countByLibraryOrganizationId(1L)).thenReturn(1L);

        LibraryOrganizationResponseDto response = service.createOrganization(req);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Alexandria Library");
        assertThat(response.getAdmin()).isNotNull();
        assertThat(response.getAdmin().getEmail()).isEqualTo("admin@alexlib.org");
        assertThat(response.getAdmin().getRole()).isEqualTo(UserRole.ADMIN_LIBRARIAN.name());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertThat(createdUser.getEmail()).isEqualTo("admin@alexlib.org");
        assertThat(createdUser.getRole()).isEqualTo(UserRole.ADMIN_LIBRARIAN.getCode());
        assertThat(createdUser.getLibraryOrganization().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("assignLibrarian_existingEmail_throwsBadRequestException")
    void assignLibrarian_existingEmail_throwsBadRequestException() {
        AssignLibrarianRequest req = new AssignLibrarianRequest(
                "reader@domain.com", "Reader", null, "User", "Secret123!"
        );

        when(libraryOrgRepository.findById(1L)).thenReturn(Optional.of(org));
        when(userRepository.existsByEmail("reader@domain.com")).thenReturn(true);

        assertThatThrownBy(() -> service.assignLibrarian(1L, req))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("updateOrganization_withAdminUpdate_updatesLibraryAndAdminInOneRequest")
    void updateOrganization_withAdminUpdate_updatesLibraryAndAdminInOneRequest() {
        UpdateLibraryAdminRequest adminUpdate = new UpdateLibraryAdminRequest(
                "admin@alexlib.org", "Ahmed Updated", null, "Hassan Updated", "NewSecret123!"
        );
        UpdateLibraryOrganizationRequest req = new UpdateLibraryOrganizationRequest(
                "Alexandria Library Updated", "Updated Desc", null, null, null, null, null, null, null, adminUpdate
        );

        when(libraryOrgRepository.findById(1L)).thenReturn(Optional.of(org));
        when(libraryOrgRepository.save(any(LibraryOrganization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllByLibraryOrganizationId(1L)).thenReturn(List.of(adminUser));
        when(passwordEncoder.encode("NewSecret123!")).thenReturn("new-hashed-pwd");

        LibraryOrganizationResponseDto response = service.updateOrganization(1L, req);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Alexandria Library Updated");
        verify(userRepository).save(adminUser);
        assertThat(adminUser.getFirstName()).isEqualTo("Ahmed Updated");
        assertThat(adminUser.getLastName()).isEqualTo("Hassan Updated");
    }

    @Test
    @DisplayName("updateOrganization_withNewAdminEmail_reassignsAdminToNewUser")
    void updateOrganization_withNewAdminEmail_reassignsAdminToNewUser() {
        UpdateLibraryAdminRequest adminUpdate = new UpdateLibraryAdminRequest(
                "newadmin@alexlib.org", "New", null, "Admin", "Pass123!"
        );
        UpdateLibraryOrganizationRequest req = new UpdateLibraryOrganizationRequest(
                null, null, null, null, null, null, null, null, null, adminUpdate
        );

        User existingNewUser = User.builder()
                .email("newadmin@alexlib.org")
                .firstName("New")
                .lastName("Admin")
                .role(UserRole.READER.getCode())
                .active(Status.ACTIVE.getCode())
                .build();
        existingNewUser.setId(200L);

        when(libraryOrgRepository.findById(1L)).thenReturn(Optional.of(org));
        when(libraryOrgRepository.save(any(LibraryOrganization.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findAllByLibraryOrganizationId(1L)).thenReturn(List.of(adminUser));
        when(userRepository.findByEmail("newadmin@alexlib.org")).thenReturn(Optional.of(existingNewUser));
        when(passwordEncoder.encode("Pass123!")).thenReturn("hashed-new-pass");

        service.updateOrganization(1L, req);

        // Old admin is detached and demoted
        assertThat(adminUser.getRole()).isEqualTo(UserRole.READER.getCode());
        assertThat(adminUser.getLibraryOrganization()).isNull();
        // New admin is attached
        assertThat(existingNewUser.getRole()).isEqualTo(UserRole.ADMIN_LIBRARIAN.getCode());
        assertThat(existingNewUser.getLibraryOrganization()).isEqualTo(org);

        verify(userRepository).save(adminUser);
        verify(userRepository).save(existingNewUser);
    }

    @Test
    @DisplayName("deleteOrganization_validId_cleansStorageFilesAndDeleteOrganization")
    void deleteOrganization_validId_cleansStorageFilesAndDeleteOrganization() {
        Book book1 = new Book();
        book1.setId(501L);
        book1.setTitle("Book 1");
        book1.setBookSource(BookSource.LIBRARY);
        book1.setStatus(BookStatus.PUBLISHED);
        book1.setLibraryOrganization(org);

        Book book2 = new Book();
        book2.setId(502L);
        book2.setTitle("Book 2");
        book2.setBookSource(BookSource.LIBRARY);
        book2.setStatus(BookStatus.PUBLISHED);
        book2.setLibraryOrganization(org);

        List<Book> books = List.of(book1, book2);

        when(libraryOrgRepository.findById(1L)).thenReturn(Optional.of(org));
        when(bookRepository.findAllByLibraryOrganizationId(1L)).thenReturn(books);

        service.deleteOrganization(1L);

        // 1. Files cleaned up for each book before DB cascade
        verify(bookFileService).handleDeleteFiles(book1);
        verify(bookFileService).handleDeleteFiles(book2);

        // 2. Organization deleted (books cascade via orphanRemoval/DB FK; users unlinked via ON DELETE SET NULL)
        verify(libraryOrgRepository).delete(org);
        verify(libraryOrgRepository).flush();
    }

    @Test
    @DisplayName("deleteOrganization_notFound_throwsResourceNotFoundException")
    void deleteOrganization_notFound_throwsResourceNotFoundException() {
        when(libraryOrgRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteOrganization(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(bookFileService);
        verify(libraryOrgRepository, never()).delete(any(LibraryOrganization.class));
        verify(userRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("getAllOrganizationsForAdmin_returnsPaginatedLibrariesWithAdmins")
    void getAllOrganizationsForAdmin_returnsPaginatedLibrariesWithAdmins() {
        org.springframework.data.domain.Page<LibraryOrganization> pageResult =
                new org.springframework.data.domain.PageImpl<>(List.of(org));

        when(libraryOrgRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(pageResult);
        when(userRepository.findAllByLibraryOrganizationId(1L)).thenReturn(List.of(adminUser));
        when(bookRepository.countByLibraryOrganizationId(1L)).thenReturn(5L);
        when(userRepository.countByLibraryOrganizationId(1L)).thenReturn(1L);

        com.doova.ktab.utils.pagination.PageResponse<LibraryOrganizationResponseDto> response =
                service.getAllOrganizationsForAdmin(0, 10, null);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(response.getContent().get(0).getAdmin()).isNotNull();
        assertThat(response.getContent().get(0).getAdmin().getEmail()).isEqualTo("admin@alexlib.org");
        assertThat(response.getContent().get(0).getAdmin().getRole()).isEqualTo(UserRole.ADMIN_LIBRARIAN.name());
    }

    @Test
    @DisplayName("updateStaff_validRequest_updatesStaffSuccessfully")
    void updateStaff_validRequest_updatesStaffSuccessfully() {
        User staffUser = User.builder()
                .email("staff@alexlib.org")
                .firstName("Staff")
                .lastName("Old")
                .role(UserRole.LIBRARIAN.getCode())
                .libraryOrganization(org)
                .build();
        staffUser.setId(201L);

        UpdateLibrarianStaffRequest req = new UpdateLibrarianStaffRequest(
                "staff.updated@alexlib.org", "UpdatedStaff", null, "UpdatedLast", "NewPass123!", "ADMIN_LIBRARIAN"
        );

        when(userRepository.findById(201L)).thenReturn(Optional.of(staffUser));
        when(userRepository.existsByEmail("staff.updated@alexlib.org")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123!")).thenReturn("encoded-new-pass");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        LibrarianStaffResponseDto result = service.updateStaff(1L, 201L, req);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("staff.updated@alexlib.org");
        assertThat(result.getFirstName()).isEqualTo("UpdatedStaff");
        assertThat(result.getLastName()).isEqualTo("UpdatedLast");
        assertThat(result.getRole()).isEqualTo(UserRole.ADMIN_LIBRARIAN.name());
        verify(userRepository).save(staffUser);
    }

    @Test
    @DisplayName("updateStaff_emailAlreadyExists_throwsBadRequestException")
    void updateStaff_emailAlreadyExists_throwsBadRequestException() {
        User staffUser = User.builder()
                .email("staff@alexlib.org")
                .role(UserRole.LIBRARIAN.getCode())
                .libraryOrganization(org)
                .build();
        staffUser.setId(201L);

        UpdateLibrarianStaffRequest req = new UpdateLibrarianStaffRequest(
                "taken@domain.com", null, null, null, null, null
        );

        when(userRepository.findById(201L)).thenReturn(Optional.of(staffUser));
        when(userRepository.existsByEmail("taken@domain.com")).thenReturn(true);

        assertThatThrownBy(() -> service.updateStaff(1L, 201L, req))
                .isInstanceOf(BadRequestException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStaff_wrongOrg_throwsResourceNotFoundException")
    void updateStaff_wrongOrg_throwsResourceNotFoundException() {
        LibraryOrganization otherOrg = LibraryOrganization.builder().name("Other Lib").build();
        otherOrg.setId(99L);

        User staffUser = User.builder()
                .email("staff@other.org")
                .role(UserRole.LIBRARIAN.getCode())
                .libraryOrganization(otherOrg)
                .build();
        staffUser.setId(201L);

        UpdateLibrarianStaffRequest req = new UpdateLibrarianStaffRequest(
                "new@alexlib.org", null, null, null, null, null
        );

        when(userRepository.findById(201L)).thenReturn(Optional.of(staffUser));

        assertThatThrownBy(() -> service.updateStaff(1L, 201L, req))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("removeLibrarian_validStaff_permanentlyDeletesStaffAccount")
    void removeLibrarian_validStaff_permanentlyDeletesStaffAccount() {
        User staffUser = User.builder()
                .email("staff@alexlib.org")
                .role(UserRole.LIBRARIAN.getCode())
                .libraryOrganization(org)
                .build();
        staffUser.setId(201L);

        when(userRepository.findById(201L)).thenReturn(Optional.of(staffUser));

        service.removeLibrarian(1L, 201L);

        verify(userRepository).delete(staffUser);
    }

    @Test
    @DisplayName("removeLibrarian_wrongOrg_throwsResourceNotFoundException")
    void removeLibrarian_wrongOrg_throwsResourceNotFoundException() {
        LibraryOrganization otherOrg = LibraryOrganization.builder().name("Other Lib").build();
        otherOrg.setId(99L);

        User staffUser = User.builder()
                .email("staff@other.org")
                .role(UserRole.LIBRARIAN.getCode())
                .libraryOrganization(otherOrg)
                .build();
        staffUser.setId(201L);

        when(userRepository.findById(201L)).thenReturn(Optional.of(staffUser));

        assertThatThrownBy(() -> service.removeLibrarian(1L, 201L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).delete(any());
    }
}
