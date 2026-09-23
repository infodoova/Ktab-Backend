package com.doova.ktab.controller.v1.librarian;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.library.AssignLibrarianRequest;
import com.doova.ktab.dto.library.LibrarianStaffResponseDto;
import com.doova.ktab.dto.library.UpdateLibrarianStaffRequest;
import com.doova.ktab.model.user.User;
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
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LibraryAdminManagementControllerTest {

    @Mock
    private LibraryOrganizationService libraryOrgService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private LibraryAdminManagementController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private User testAdminLibrarian;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        testAdminLibrarian = new User();
        testAdminLibrarian.setId(10L);
        testAdminLibrarian.setEmail("adminlib@library.com");
        testAdminLibrarian.setRole("35");

        HandlerMethodArgumentResolver currentUserResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(CurrentUser.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return testAdminLibrarian;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(currentUserResolver)
                .build();

        when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenReturn("Success message");
    }

    @Test
    @DisplayName("updateStaff_validRequest_returnsUpdatedStaffAndHttp200")
    void updateStaff_validRequest_returnsUpdatedStaffAndHttp200() throws Exception {
        UpdateLibrarianStaffRequest req = new UpdateLibrarianStaffRequest(
                "updated.staff@lib.org", "UpdatedFirst", null, "UpdatedLast", "Secret123!", "LIBRARIAN"
        );

        LibrarianStaffResponseDto responseDto = LibrarianStaffResponseDto.builder()
                .userId(55L)
                .email("updated.staff@lib.org")
                .firstName("UpdatedFirst")
                .lastName("UpdatedLast")
                .fullName("UpdatedFirst UpdatedLast")
                .role("LIBRARIAN")
                .roleCode("30")
                .build();

        when(libraryOrgService.updateStaffByAdminLibrarian(eq(testAdminLibrarian), eq(55L), any(UpdateLibrarianStaffRequest.class)))
                .thenReturn(responseDto);

        mockMvc.perform(patch("/library-admin/staff/55")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(55))
                .andExpect(jsonPath("$.data.email").value("updated.staff@lib.org"))
                .andExpect(jsonPath("$.data.firstName").value("UpdatedFirst"))
                .andExpect(jsonPath("$.data.lastName").value("UpdatedLast"));

        verify(libraryOrgService).updateStaffByAdminLibrarian(eq(testAdminLibrarian), eq(55L), any(UpdateLibrarianStaffRequest.class));
    }

    @Test
    @DisplayName("getStaff_returnsStaffListAndHttp200")
    void getStaff_returnsStaffListAndHttp200() throws Exception {
        LibrarianStaffResponseDto staffDto = LibrarianStaffResponseDto.builder()
                .userId(55L)
                .email("staff@lib.org")
                .firstName("Staff")
                .lastName("One")
                .role("LIBRARIAN")
                .build();

        when(libraryOrgService.getStaffMembers(testAdminLibrarian))
                .thenReturn(List.of(staffDto));

        mockMvc.perform(get("/library-admin/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(55))
                .andExpect(jsonPath("$.data[0].email").value("staff@lib.org"));

        verify(libraryOrgService).getStaffMembers(testAdminLibrarian);
    }

    @Test
    @DisplayName("addStaff_validRequest_returnsHttp201")
    void addStaff_validRequest_returnsHttp201() throws Exception {
        AssignLibrarianRequest req = new AssignLibrarianRequest(
                "newstaff@lib.org", "New", null, "Staff", "Pass123!"
        );

        mockMvc.perform(post("/library-admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(libraryOrgService).assignStaffByAdminLibrarian(eq(testAdminLibrarian), any(AssignLibrarianRequest.class));
    }

    @Test
    @DisplayName("removeStaff_validId_returnsHttp200")
    void removeStaff_validId_returnsHttp200() throws Exception {
        mockMvc.perform(delete("/library-admin/staff/55"))
                .andExpect(status().isOk());

        verify(libraryOrgService).removeStaffByAdminLibrarian(testAdminLibrarian, 55L);
    }
}
