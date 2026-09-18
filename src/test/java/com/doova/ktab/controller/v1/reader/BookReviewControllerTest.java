package com.doova.ktab.controller.v1.reader;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.review.ReviewRequestDto;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.reviews.ReviewService;
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

import java.util.Locale;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookReviewControllerTest {

    @Mock
    private ReviewService reviewService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private BookReviewController bookReviewController;

    private MockMvc mockMvc;
    private User testReader;

    @BeforeEach
    void setUp() {
        testReader = new User();
        testReader.setId(1L);
        testReader.setEmail("reader@ktab.app");
        testReader.setRole("0");

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
                return testReader;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(bookReviewController)
                .setCustomArgumentResolvers(currentUserResolver)
                .build();

        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("postReview_canonicalEndpoint_returns201")
    void postReview_canonicalEndpoint_returns201() throws Exception {
        String json = "{\"rating\":5,\"comment\":\"Excellent book\"}";

        mockMvc.perform(post("/reader/books/112/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        verify(reviewService).createReview(eq(112L), any(ReviewRequestDto.class), eq(testReader));
    }

    @Test
    @DisplayName("postReview_reviewsAddReviewAlias_returns201")
    void postReview_reviewsAddReviewAlias_returns201() throws Exception {
        String json = "{\"rating\":5,\"comment\":\"Excellent book\"}";

        mockMvc.perform(post("/reviews/addReview/112")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        verify(reviewService).createReview(eq(112L), any(ReviewRequestDto.class), eq(testReader));
    }

    @Test
    @DisplayName("postReview_readerAddReviewAlias_returns201")
    void postReview_readerAddReviewAlias_returns201() throws Exception {
        String json = "{\"rating\":4,\"comment\":\"Very good\"}";

        mockMvc.perform(post("/reader/addReview/112")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        verify(reviewService).createReview(eq(112L), any(ReviewRequestDto.class), eq(testReader));
    }
}
