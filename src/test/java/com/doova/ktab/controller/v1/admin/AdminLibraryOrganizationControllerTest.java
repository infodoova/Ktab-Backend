package com.doova.ktab.controller.v1.admin;

import com.doova.ktab.dto.library.AssignLibrarianRequest;
import com.doova.ktab.dto.library.CreateLibraryOrganizationRequest;
import com.doova.ktab.dto.library.LibraryOrganizationResponseDto;
import com.doova.ktab.dto.library.UpdateLibraryOrganizationRequest;
import com.doova.ktab.service.library.LibraryOrganizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminLibraryOrganizationControllerTest {

    @Mock
    private LibraryOrganizationService libraryOrgService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private AdminLibraryOrganizationController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("getAllLibraries_validRequest_returnsPaginatedLibrariesWithAdmins")
    void getAllLibraries_validRequest_returnsPaginatedLibrariesWithAdmins() throws Exception {
        LibraryOrganizationResponseDto dto = new LibraryOrganizationResponseDto();
        dto.setId(10L);
        dto.setName("National Library");

        com.doova.ktab.dto.library.LibrarianStaffResponseDto adminDto = new com.doova.ktab.dto.library.LibrarianStaffResponseDto();
        adminDto.setUserId(99L);
        adminDto.setEmail("admin@nl.sa");
        adminDto.setRole("ADMIN_LIBRARIAN");
        dto.setAdmin(adminDto);

        com.doova.ktab.utils.pagination.PageResponse<LibraryOrganizationResponseDto> pageResponse =
                new com.doova.ktab.utils.pagination.PageResponse<>(
                        java.util.List.of(dto), 0, 10, 1L, 1, true
                );

        when(libraryOrgService.getAllOrganizationsForAdmin(0, 10, null)).thenReturn(pageResponse);

        mockMvc.perform(get("/admin/libraries?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(10))
                .andExpect(jsonPath("$.data.content[0].admin.email").value("admin@nl.sa"));

        verify(libraryOrgService).getAllOrganizationsForAdmin(0, 10, null);
    }

    @Test
    @DisplayName("createLibrary_validPayload_returnsCreated")
    void createLibrary_validPayload_returnsCreated() throws Exception {
        AssignLibrarianRequest adminReq = new AssignLibrarianRequest(
                "admin@nl.sa", "Admin", null, "User", "Pass123!", null
        );
        CreateLibraryOrganizationRequest req = new CreateLibraryOrganizationRequest(
                "National Library", "Historic collection", "Riyadh", "Saudi Arabia", "King Fahd Rd", "https://nl.sa", "info@nl.sa", "+966110000000", adminReq
        );

        LibraryOrganizationResponseDto dto = new LibraryOrganizationResponseDto();
        dto.setId(10L);
        dto.setName("National Library");

        when(libraryOrgService.createOrganization(any())).thenReturn(dto);

        mockMvc.perform(post("/admin/libraries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.id").value(10));

        verify(libraryOrgService).createOrganization(any(CreateLibraryOrganizationRequest.class));
    }

    @Test
    @DisplayName("updateLibrary_validPayload_returnsOk")
    void updateLibrary_validPayload_returnsOk() throws Exception {
        UpdateLibraryOrganizationRequest req = new UpdateLibraryOrganizationRequest(
                "National Library Updated", null, null, null, null, null, null, null, null
        );

        LibraryOrganizationResponseDto dto = new LibraryOrganizationResponseDto();
        dto.setId(10L);
        dto.setName("National Library Updated");

        when(libraryOrgService.updateOrganization(eq(10L), any())).thenReturn(dto);

        mockMvc.perform(patch("/admin/libraries/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.name").value("National Library Updated"));

        verify(libraryOrgService).updateOrganization(eq(10L), any(UpdateLibraryOrganizationRequest.class));
    }

    @Test
    @DisplayName("deleteLibrary_validId_returnsOk")
    void deleteLibrary_validId_returnsOk() throws Exception {
        mockMvc.perform(delete("/admin/libraries/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));

        verify(libraryOrgService).deleteOrganization(10L);
    }
}
