package com.doova.ktab.controller.v1.publisher;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSourceFileResponseDto;
import com.doova.ktab.dto.book.PublisherReviewSearchRequest;
import com.doova.ktab.dto.publisher.ReviewDecisionRequest;
import com.doova.ktab.enums.status.BookStatus;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.publisher.PublisherReviewService;
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
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublisherReviewControllerTest {

    @Mock
    private PublisherReviewService publisherReviewService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private PublisherReviewController publisherReviewController;

    private MockMvc mockMvc;
    private User testPublisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        testPublisher = new User();
        testPublisher.setId(50L);
        testPublisher.setEmail("publisher@ktab.com");
        testPublisher.setRole("40");

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
                return testPublisher;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(publisherReviewController)
                .setCustomArgumentResolvers(currentUserResolver)
                .build();

        objectMapper = new ObjectMapper();
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("getReviewQueue_validEndpoint_returnsSuccess")
    void getReviewQueue_validEndpoint_returnsSuccess() throws Exception {
        BookResponseDto dto = new BookResponseDto();
        dto.setId(101L);
        dto.setTitle("Queue Book");

        PageResponse<BookResponseDto> pageResponse = new PageResponse<>(List.of(dto), 0, 10, 1, 1, true);
        when(publisherReviewService.getReviewQueue(0, 10)).thenReturn(pageResponse);

        mockMvc.perform(get("/publishers/review-queue")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(101));
    }

    @Test
    @DisplayName("searchReviewQueue_validRequest_returnsSuccess")
    void searchReviewQueue_validRequest_returnsSuccess() throws Exception {
        BookResponseDto dto = new BookResponseDto();
        dto.setId(102L);

        PageResponse<BookResponseDto> pageResponse = new PageResponse<>(List.of(dto), 0, 10, 1, 1, true);
        when(publisherReviewService.searchReviewQueue(any())).thenReturn(pageResponse);

        PublisherReviewSearchRequest searchReq = new PublisherReviewSearchRequest(
                "test", BookStatus.UNDER_REVIEW, null, null, null, null, null, null, 0, 10, "submittedAt", null
        );

        mockMvc.perform(post("/publishers/review-queue/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(102));
    }

    @Test
    @DisplayName("getBookById_existingId_returnsSuccess")
    void getBookById_existingId_returnsSuccess() throws Exception {
        BookResponseDto dto = new BookResponseDto();
        dto.setId(103L);
        dto.setTitle("Detail Book");

        when(publisherReviewService.getBookById(103L)).thenReturn(dto);

        mockMvc.perform(get("/publishers/books/103")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(103));
    }

    @Test
    @DisplayName("getSourceFile_existingBook_returnsEphemeralUrl")
    void getSourceFile_existingBook_returnsEphemeralUrl() throws Exception {
        BookSourceFileResponseDto dto = new BookSourceFileResponseDto(
                104L, "doc.pdf", "https://storage.ktab.com/doc.pdf", Instant.now().plusSeconds(180)
        );

        when(publisherReviewService.getSourceFileForPublisher(eq(104L), eq(testPublisher))).thenReturn(dto);

        mockMvc.perform(get("/publishers/books/104/source-file")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.fileName").value("doc.pdf"));
    }

    @Test
    @DisplayName("approveBook_validBook_returnsSuccess")
    void approveBook_validBook_returnsSuccess() throws Exception {
        BookResponseDto dto = new BookResponseDto();
        dto.setId(105L);
        dto.setStatus(BookStatus.PUBLISHED);

        ReviewDecisionRequest req = new ReviewDecisionRequest("Approved");
        when(publisherReviewService.approveBook(eq(105L), any(), eq(testPublisher))).thenReturn(dto);

        mockMvc.perform(post("/publishers/books/105/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("rejectBook_validBook_returnsSuccess")
    void rejectBook_validBook_returnsSuccess() throws Exception {
        BookResponseDto dto = new BookResponseDto();
        dto.setId(106L);
        dto.setStatus(BookStatus.DRAFT);

        ReviewDecisionRequest req = new ReviewDecisionRequest("Please fix typos");
        when(publisherReviewService.rejectBook(eq(106L), any(), eq(testPublisher))).thenReturn(dto);

        mockMvc.perform(post("/publishers/books/106/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }
}
