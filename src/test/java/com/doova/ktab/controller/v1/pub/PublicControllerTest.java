package com.doova.ktab.controller.v1.pub;

import com.doova.ktab.dto.book.BookCoverResponse;
import com.doova.ktab.dto.metadata.AppEnumsResponseDto.RoleMetadataDto;
import com.doova.ktab.service.pub.PublicService;
import com.doova.ktab.utils.pagination.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublicControllerTest {

    @Mock
    private PublicService publicService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private PublicController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    // ========================================================================================
    // GET /public/books/covers
    // ========================================================================================

    @Test
    @DisplayName("getBookCovers_defaultPagination_returnsOk")
    void getBookCovers_defaultPagination_returnsOk() throws Exception {
        BookCoverResponse cover = BookCoverResponse.builder()
                .id(10L)
                .title("Sunrise")
                .coverImageUrl("https://cdn.example.com/covers/10.jpg")
                .mainGenre("Poetry")
                .build();

        PageResponse<BookCoverResponse> pageResponse = new PageResponse<>(
                List.of(cover), 0, 18, 1L, 1, true
        );
        when(publicService.getBookCovers(0, 18)).thenReturn(pageResponse);

        mockMvc.perform(get("/public/books/covers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(10))
                .andExpect(jsonPath("$.data.content[0].title").value("Sunrise"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(publicService).getBookCovers(0, 18);
    }

    @Test
    @DisplayName("getBookCovers_customPagination_delegatesCorrectly")
    void getBookCovers_customPagination_delegatesCorrectly() throws Exception {
        PageResponse<BookCoverResponse> pageResponse = new PageResponse<>(
                List.of(), 2, 6, 0L, 0, true
        );
        when(publicService.getBookCovers(2, 6)).thenReturn(pageResponse);

        mockMvc.perform(get("/public/books/covers?page=2&size=6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(6));

        verify(publicService).getBookCovers(2, 6);
    }

    // ========================================================================================
    // GET /public/roles
    // ========================================================================================

    @Test
    @DisplayName("getRoles_returnsOk")
    void getRoles_returnsOk() throws Exception {
        RoleMetadataDto author = new RoleMetadataDto("AUTHOR", "10", "مؤلف", "Author");
        RoleMetadataDto reader = new RoleMetadataDto("READER", "20", "قارئ", "Reader");

        when(publicService.getRoles()).thenReturn(List.of(author, reader));

        mockMvc.perform(get("/public/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data[0].role").value("AUTHOR"))
                .andExpect(jsonPath("$.data[1].role").value("READER"));

        verify(publicService).getRoles();
    }

    // ========================================================================================
    // GET /public/books/top-reviewed/covers and /public/books/cover-images
    // ========================================================================================

    @Test
    @DisplayName("getTopReviewedCoverImages_defaultLimit_returnsOnlyImageUrls")
    void getTopReviewedCoverImages_defaultLimit_returnsOnlyImageUrls() throws Exception {
        when(publicService.getTopReviewedCoverImages(10)).thenReturn(List.of("https://cdn/a.jpg", "https://cdn/b.jpg"));

        mockMvc.perform(get("/public/books/top-reviewed/covers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("https://cdn/a.jpg"))
                .andExpect(jsonPath("$.data[1]").value("https://cdn/b.jpg"))
                .andExpect(jsonPath("$.data.length()").value(2));

        verify(publicService).getTopReviewedCoverImages(10);
    }

    @Test
    @DisplayName("getTopReviewedCoverImages_customLimit_passesItOn")
    void getTopReviewedCoverImages_customLimit_passesItOn() throws Exception {
        when(publicService.getTopReviewedCoverImages(3)).thenReturn(List.of());

        mockMvc.perform(get("/public/books/top-reviewed/covers").param("limit", "3"))
                .andExpect(status().isOk());

        verify(publicService).getTopReviewedCoverImages(3);
    }

    @Test
    @DisplayName("getCoverImages_defaultPaging_returnsImageUrlsWithPagingDetails")
    void getCoverImages_defaultPaging_returnsImageUrlsWithPagingDetails() throws Exception {
        when(publicService.getCoverImages(0, 50)).thenReturn(new PageResponse<>(List.of("https://cdn/a.jpg"), 0, 50, 1L, 1, true));

        mockMvc.perform(get("/public/books/cover-images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0]").value("https://cdn/a.jpg"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(publicService).getCoverImages(0, 50);
    }
}
