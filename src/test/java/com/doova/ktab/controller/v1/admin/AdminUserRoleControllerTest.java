package com.doova.ktab.controller.v1.admin;

import com.doova.ktab.dto.user.AssignPublisherRequest;
import com.doova.ktab.service.publisher.PublisherAdminService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserRoleControllerTest {

    @Mock
    private PublisherAdminService publisherAdminService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private AdminUserRoleController adminUserRoleController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminUserRoleController).build();
        objectMapper = new ObjectMapper();
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("getAllPublishers_validRequest_returnsPaginatedPublishers")
    void getAllPublishers_validRequest_returnsPaginatedPublishers() throws Exception {
        com.doova.ktab.dto.user.UserResponseDto publisherDto = new com.doova.ktab.dto.user.UserResponseDto(
                55L, "publisher@ktab.com", "First", "Mid", "Last", "First Mid Last", "PUBLISHER", "ACTIVE"
        );
        com.doova.ktab.utils.pagination.PageResponse<com.doova.ktab.dto.user.UserResponseDto> pageResponse =
                new com.doova.ktab.utils.pagination.PageResponse<>(
                        java.util.List.of(publisherDto), 0, 10, 1L, 1, true
                );

        when(publisherAdminService.getAllPublishers(0, 10, null)).thenReturn(pageResponse);

        mockMvc.perform(get("/admin/publishers?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(55))
                .andExpect(jsonPath("$.data.content[0].email").value("publisher@ktab.com"));

        verify(publisherAdminService).getAllPublishers(0, 10, null);
    }

    @Test
    @DisplayName("assignPublisher_validRequest_returnsOk")
    void assignPublisher_validRequest_returnsOk() throws Exception {
        AssignPublisherRequest req = new AssignPublisherRequest(
                "publisher@ktab.com",
                "FirstName",
                "Middle",
                "LastName",
                "ValidP@ss123"
        );

        mockMvc.perform(post("/admin/publishers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));

        verify(publisherAdminService).assignPublisher(req);
    }

    @Test
    @DisplayName("removePublisher_validId_returnsOk")
    void removePublisher_validId_returnsOk() throws Exception {
        mockMvc.perform(delete("/admin/publishers/77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200));

        verify(publisherAdminService).removePublisher(77L);
    }
}
