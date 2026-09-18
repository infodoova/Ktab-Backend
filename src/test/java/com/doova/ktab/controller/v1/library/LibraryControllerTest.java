package com.doova.ktab.controller.v1.library;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.library.AssignBookRequest;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.library.LibraryService;
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
class LibraryControllerTest {

    @Mock
    private LibraryService libraryService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private LibraryController libraryController;

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

        mockMvc = MockMvcBuilders.standaloneSetup(libraryController)
                .setCustomArgumentResolvers(currentUserResolver)
                .build();

        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("assignBookToUser_assignBookAlias_returns201")
    void assignBookToUser_assignBookAlias_returns201() throws Exception {
        mockMvc.perform(post("/library/assignBook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":112}"))
                .andExpect(status().isCreated());

        verify(libraryService).assignBookToUser(eq(new AssignBookRequest(112L)), eq(testReader));
    }

    @Test
    @DisplayName("assignBookToUser_booksCanonical_returns201")
    void assignBookToUser_booksCanonical_returns201() throws Exception {
        mockMvc.perform(post("/library/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":112}"))
                .andExpect(status().isCreated());

        verify(libraryService).assignBookToUser(eq(new AssignBookRequest(112L)), eq(testReader));
    }
}
