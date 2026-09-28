package com.doova.ktab.features.talktobook.controller;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.features.talktobook.dto.request.TalkToBookRequest;
import com.doova.ktab.features.talktobook.dto.response.BookCitation;
import com.doova.ktab.features.talktobook.dto.response.TalkToBookResponse;
import com.doova.ktab.features.talktobook.service.TalkToBookService;
import com.doova.ktab.model.user.User;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TalkToBookControllerTest {

    @Mock
    private TalkToBookService talkToBookService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private TalkToBookController talkToBookController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private User testUser;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testUser = new User();
        testUser.setId(42L);
        testUser.setEmail("reader@ktab.app");

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
                return testUser;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(talkToBookController)
                .setCustomArgumentResolvers(currentUserResolver)
                .build();
    }

    @Test
    @DisplayName("askQuestion_validRequest_returns200OkWithResponseEnvelope")
    void askQuestion_validRequest_returns200OkWithResponseEnvelope() throws Exception {
        TalkToBookRequest request = new TalkToBookRequest("What is the central theme?");
        TalkToBookResponse response = new TalkToBookResponse(
                "What is the central theme?",
                "The central theme is destiny and personal legends [1].",
                List.of(new BookCitation(1, "The central theme is destiny", 16)),
                List.of(15, 16),
                false,
                "INTERNAL_RAG",
                1
        );

        when(talkToBookService.askQuestion(eq(10L), any(TalkToBookRequest.class), eq(42L)))
                .thenReturn(response);
        when(messageSource.getMessage(any(), any(), any(Locale.class)))
                .thenReturn("Answer generated successfully");

        mockMvc.perform(post("/books/10/talk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.question").value("What is the central theme?"))
                .andExpect(jsonPath("$.data.answer").value("The central theme is destiny and personal legends [1]."))
                .andExpect(jsonPath("$.data.citations[0].id").value(1))
                .andExpect(jsonPath("$.data.citations[0].snippet").value("The central theme is destiny"))
                .andExpect(jsonPath("$.data.citations[0].page").doesNotExist())
                .andExpect(jsonPath("$.data.citedPages").doesNotExist())
                .andExpect(jsonPath("$.data.cached").value(false))
                .andExpect(jsonPath("$.data.source").value("INTERNAL_RAG"))
                .andExpect(jsonPath("$.data.hitCount").value(1));
    }

    @Test
    @DisplayName("askQuestion_blankQuestion_returns400BadRequest")
    void askQuestion_blankQuestion_returns400BadRequest() throws Exception {
        String invalidJson = "{\"question\": \"  \"}";

        mockMvc.perform(post("/books/10/talk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("askQuestion_excessiveRepetition_returns400BadRequest")
    void askQuestion_excessiveRepetition_returns400BadRequest() throws Exception {
        String spamJson = "{\"question\": \"aaaaaaaaaaaaaaaaaaaa\"}";

        mockMvc.perform(post("/books/10/talk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spamJson))
                .andExpect(status().isBadRequest());
    }
}
