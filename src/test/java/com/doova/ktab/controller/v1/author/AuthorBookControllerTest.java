package com.doova.ktab.controller.v1.author;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSourceFileResponseDto;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.book.BookService;
import com.doova.ktab.utils.pagination.PageResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthorBookControllerTest {

    @Mock
    private BookService bookService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private AuthorBookController authorBookController;

    private MockMvc mockMvc;
    private User testAuthor;

    @BeforeEach
    void setUp() {
        testAuthor = new User();
        testAuthor.setId(10L);
        testAuthor.setEmail("author@darhashem.com");
        testAuthor.setRole("10");

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
                return testAuthor;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(authorBookController)
                .setCustomArgumentResolvers(currentUserResolver)
                .build();

        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("getSourceFile_canonicalEndpoint_returnsEphemeralDownloadUrl")
    void getSourceFile_canonicalEndpoint_returnsEphemeralDownloadUrl() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(180);
        BookSourceFileResponseDto dto = new BookSourceFileResponseDto(
                94L,
                "novel.pdf",
                "https://storage.ktab.com/signed-ephemeral-download-url",
                expiresAt
        );

        when(bookService.getSourceFileForAuthor(eq(94L), eq(testAuthor))).thenReturn(dto);

        mockMvc.perform(get("/authors/me/books/94/source-file")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.bookId").value(94))
                .andExpect(jsonPath("$.data.fileName").value("novel.pdf"))
                .andExpect(jsonPath("$.data.downloadUrl").value("https://storage.ktab.com/signed-ephemeral-download-url"));
    }

    @Test
    @DisplayName("getBooksByAuthor_canonicalEndpoint_returnsSuccess")
    void getBooksByAuthor_canonicalEndpoint_returnsSuccess() throws Exception {
        BookResponseDto bookDto = new BookResponseDto();
        bookDto.setId(94L);
        bookDto.setTitle("Author Novel");

        PageResponse<BookResponseDto> pageResponse = new PageResponse<>(List.of(bookDto), 0, 6, 1, 1, true);

        when(bookService.getBooksByAuthorId(eq(testAuthor), anyInt(), anyInt(), any())).thenReturn(pageResponse);
        when(bookService.getBooksByAuthorId(eq(11L), anyInt(), anyInt(), any())).thenReturn(pageResponse);

        mockMvc.perform(get("/authors/me/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(94));

        // authorId now passed as a query param instead of a path-variable alias
        mockMvc.perform(get("/authors/me/books").param("authorId", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(94));
    }
}
