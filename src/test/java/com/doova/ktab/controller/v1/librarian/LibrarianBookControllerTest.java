package com.doova.ktab.controller.v1.librarian;

import com.doova.ktab.annotation.CurrentUser;
import com.doova.ktab.dto.book.BookResponseDto;
import com.doova.ktab.dto.book.BookSourceFileResponseDto;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.librarian.LibrarianBookService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LibrarianBookControllerTest {

    @Mock
    private LibrarianBookService librarianBookService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private LibrarianBookController librarianBookController;

    private MockMvc mockMvc;
    private User testLibrarian;

    @BeforeEach
    void setUp() {
        testLibrarian = new User();
        testLibrarian.setId(30L);
        testLibrarian.setEmail("librarian@library.com");
        testLibrarian.setRole("30");

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
                return testLibrarian;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(librarianBookController)
                .setCustomArgumentResolvers(currentUserResolver)
                .build();

        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("getSourceFile_canonicalEndpoint_returnsEphemeralDownloadUrl")
    void getSourceFile_canonicalEndpoint_returnsEphemeralDownloadUrl() throws Exception {
        Instant expiresAt = Instant.now().plusSeconds(180);
        BookSourceFileResponseDto dto = new BookSourceFileResponseDto(
                100L,
                "library-reference.pdf",
                "https://storage.ktab.com/librarian-signed-download-url",
                expiresAt
        );

        when(librarianBookService.getSourceFileForLibrarian(eq(100L), eq(testLibrarian))).thenReturn(dto);

        mockMvc.perform(get("/librarians/me/books/100/source-file")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.bookId").value(100))
                .andExpect(jsonPath("$.data.fileName").value("library-reference.pdf"))
                .andExpect(jsonPath("$.data.downloadUrl").value("https://storage.ktab.com/librarian-signed-download-url"));
    }

    @Test
    @DisplayName("getMyLibraryBooks_canonicalEndpoint_returnsSuccess")
    void getMyLibraryBooks_canonicalEndpoint_returnsSuccess() throws Exception {
        BookResponseDto bookDto = new BookResponseDto();
        bookDto.setId(100L);
        bookDto.setTitle("Library Book");

        PageResponse<BookResponseDto> pageResponse = new PageResponse<>(List.of(bookDto), 0, 10, 1, 1, true);

        when(librarianBookService.getBooksForLibrary(anyInt(), anyInt(), any(), eq(testLibrarian))).thenReturn(pageResponse);

        mockMvc.perform(get("/librarians/me/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(100));
    }

    @Test
    @DisplayName("getAllLibraryBooks_adminLibrarian_returnsSuccess")
    void getAllLibraryBooks_adminLibrarian_returnsSuccess() throws Exception {
        BookResponseDto bookDto = new BookResponseDto();
        bookDto.setId(200L);
        bookDto.setTitle("Organization Library Book");

        PageResponse<BookResponseDto> pageResponse = new PageResponse<>(List.of(bookDto), 0, 10, 1, 1, true);

        when(librarianBookService.getAllBooksForLibrary(anyInt(), anyInt(), any(), eq(testLibrarian))).thenReturn(pageResponse);

        mockMvc.perform(get("/librarians/me/books/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.data.content[0].id").value(200));
    }

    @Test
    @DisplayName("deleteBook_adminLibrarian_returnsSuccess")
    void deleteBook_adminLibrarian_returnsSuccess() throws Exception {
        mockMvc.perform(delete("/librarians/me/books/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.status").value("OK"));

        verify(librarianBookService).deleteBook(100L, testLibrarian);
    }
}
