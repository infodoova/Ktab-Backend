package com.doova.ktab.controller.v1.reader;

import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSearchRequestDto;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.service.book.BookSimilarityService;
import com.doova.ktab.utils.pagination.PageResponse;
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

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookDiscoveryControllerTest {

    @Mock
    private BookService bookService;

    @Mock
    private BookSimilarityService similarityService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private BookDiscoveryController bookDiscoveryController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookDiscoveryController).build();
        objectMapper = new ObjectMapper();
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("searchBooks_aliasAndCanonical_omitsPdfDownloadUrlAndFileNameInJson")
    void searchBooks_aliasAndCanonical_omitsPdfDownloadUrlAndFileNameInJson() throws Exception {
        BookResponseDto bookDto = new BookResponseDto();
        bookDto.setId(96L);
        bookDto.setTitle("Palestinian Stories");
        bookDto.setCoverImageUrl("https://storage.ktab.com/cover.png");
        // Ensure pdfDownloadUrl and pdfFileName are null for reader
        bookDto.setPdfDownloadUrl(null);
        bookDto.setPdfFileName(null);

        PageResponse<BookResponseDto> pageResponse = new PageResponse<>(List.of(bookDto), 0, 10, 1, 1, true);

        when(bookService.searchBooks(any(), any())).thenReturn(pageResponse);

        BookSearchRequestDto searchDto = new BookSearchRequestDto("Palestine", null, null, null, null, 0, 10);
        String requestJson = objectMapper.writeValueAsString(searchDto);

        // Test alias: POST /reader/search
        String responseContent = mockMvc.perform(post("/reader/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.content[0].id").value(96))
                .andExpect(jsonPath("$.data.content[0].title").value("Palestinian Stories"))
                .andExpect(jsonPath("$.data.content[0].coverImageUrl").value("https://storage.ktab.com/cover.png"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Strictly verify confidential PDF fields are completely omitted from JSON
        assertFalse(responseContent.contains("pdfDownloadUrl"), "Reader search response must NEVER expose pdfDownloadUrl");
        assertFalse(responseContent.contains("pdfFileName"), "Reader search response must NEVER expose pdfFileName");
    }

    @Test
    @DisplayName("searchBooks_defaultSearch_enforcesAuthorBookSource")
    void searchBooks_defaultSearch_enforcesAuthorBookSource() throws Exception {
        when(bookService.searchBooks(any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true));

        // Send search request without bookSource specified in JSON
        String jsonWithoutBookSource = "{\"title\":\"Science\",\"page\":0,\"size\":10}";

        mockMvc.perform(post("/reader/books/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithoutBookSource))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<BookSearchRequestDto> captor = org.mockito.ArgumentCaptor.forClass(BookSearchRequestDto.class);
        verify(bookService, atLeastOnce()).searchBooks(captor.capture(), any());

        org.junit.jupiter.api.Assertions.assertEquals(
                com.doova.ktab.enums.book.BookSource.AUTHOR,
                captor.getValue().bookSource(),
                "Search must default to BookSource.AUTHOR and exclude LIBRARY books"
        );
    }

    @Test
    @DisplayName("getBookById_aliasAndCanonical_omitsPdfDownloadUrlAndFileNameInJson")
    void getBookById_aliasAndCanonical_omitsPdfDownloadUrlAndFileNameInJson() throws Exception {
        BookResponseDto bookDto = new BookResponseDto();
        bookDto.setId(96L);
        bookDto.setTitle("Palestinian Stories");
        bookDto.setCoverImageUrl("https://storage.ktab.com/cover.png");
        bookDto.setPdfDownloadUrl(null);
        bookDto.setPdfFileName(null);

        when(bookService.getBookById(96L)).thenReturn(bookDto);

        // Test alias: GET /reader/viewBook/96
        String responseContent = mockMvc.perform(get("/reader/viewBook/96")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.id").value(96))
                .andExpect(jsonPath("$.data.title").value("Palestinian Stories"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Strictly verify confidential PDF fields are completely omitted from JSON
        assertFalse(responseContent.contains("pdfDownloadUrl"), "Reader viewBook response must NEVER expose pdfDownloadUrl");
        assertFalse(responseContent.contains("pdfFileName"), "Reader viewBook response must NEVER expose pdfFileName");
    }

    @Test
    @DisplayName("getSimilarBooks_aliasAndCanonical_omitsPdfDownloadUrlAndFileNameInJson")
    void getSimilarBooks_aliasAndCanonical_omitsPdfDownloadUrlAndFileNameInJson() throws Exception {
        BookResponseDto bookDto = new BookResponseDto();
        bookDto.setId(97L);
        bookDto.setTitle("Similar Palestinian Stories");
        bookDto.setCoverImageUrl("https://storage.ktab.com/similar-cover.png");
        bookDto.setPdfDownloadUrl(null);
        bookDto.setPdfFileName(null);

        PageResponse<BookResponseDto> pageResponse = new PageResponse<>(List.of(bookDto), 0, 6, 1, 1, true);

        when(similarityService.getSmartSimilarBooks(eq(96L), anyInt(), anyInt())).thenReturn(pageResponse);

        // Test alias: GET /reader/similar/96
        String responseContent = mockMvc.perform(get("/reader/similar/96")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.content[0].id").value(97))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Strictly verify confidential PDF fields are completely omitted from JSON
        assertFalse(responseContent.contains("pdfDownloadUrl"), "Reader similar response must NEVER expose pdfDownloadUrl");
        assertFalse(responseContent.contains("pdfFileName"), "Reader similar response must NEVER expose pdfFileName");
    }
}
