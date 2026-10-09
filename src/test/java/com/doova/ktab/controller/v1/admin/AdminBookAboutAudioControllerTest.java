package com.doova.ktab.controller.v1.admin;

import com.doova.ktab.dto.book.BookAboutAudioResponse;
import com.doova.ktab.service.book.BookAboutAudioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Locale;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminBookAboutAudioControllerTest {

    @Mock
    private BookAboutAudioService aboutAudioService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private AdminBookAboutAudioController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        // Not every test produces a message (a refused request does not), so this stays lenient.
        org.mockito.Mockito.lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    private static BookAboutAudioResponse saved() {
        return BookAboutAudioResponse.builder().url("https://cdn/a.mp3").description("About").durationSeconds(40).mimeType("audio/mpeg").build();
    }

    @Test
    @DisplayName("upload_audioFile_storesItWithTheDescriptionAndReturnsTheLink")
    void upload_audioFile_storesItWithTheDescriptionAndReturnsTheLink() throws Exception {
        byte[] bytes = {'I', 'D', '3', 1, 2, 3};
        when(aboutAudioService.save(eq(110L), any(), eq("intro.mp3"), eq("About"), eq(40))).thenReturn(saved());

        mockMvc.perform(multipart("/admin/books/110/about-audio")
                        .file(new MockMultipartFile("file", "intro.mp3", "audio/mpeg", bytes))
                        .param("description", "About")
                        .param("durationSeconds", "40")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://cdn/a.mp3"))
                .andExpect(jsonPath("$.data.description").value("About"))
                .andExpect(jsonPath("$.data.durationSeconds").value(40));

        verify(aboutAudioService).save(eq(110L), eq(bytes), eq("intro.mp3"), eq("About"), eq(40));
    }

    @Test
    @DisplayName("upload_descriptionAndDurationAreOptional")
    void upload_descriptionAndDurationAreOptional() throws Exception {
        when(aboutAudioService.save(eq(110L), any(), anyString(), eq(null), eq(null))).thenReturn(saved());

        mockMvc.perform(multipart("/admin/books/110/about-audio")
                        .file(new MockMultipartFile("file", "intro.mp3", "audio/mpeg", new byte[]{'I', 'D', '3'}))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("upload_withoutAFile_isRefused")
    void upload_withoutAFile_isRefused() throws Exception {
        mockMvc.perform(multipart("/admin/books/110/about-audio")
                        .param("description", "About")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("get_bookWithAudio_returnsIt")
    void get_bookWithAudio_returnsIt() throws Exception {
        when(aboutAudioService.find(110L)).thenReturn(Optional.of(saved()));

        mockMvc.perform(get("/admin/books/110/about-audio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://cdn/a.mp3"));
    }

    @Test
    @DisplayName("delete_removesTheAudio")
    void delete_removesTheAudio() throws Exception {
        mockMvc.perform(delete("/admin/books/110/about-audio")).andExpect(status().isOk());

        verify(aboutAudioService).delete(110L);
    }

    @Test
    @DisplayName("onlyAdminsMayUseIt")
    void onlyAdminsMayUseIt() {
        var restriction = AdminBookAboutAudioController.class.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);

        org.assertj.core.api.Assertions.assertThat(restriction).isNotNull();
        org.assertj.core.api.Assertions.assertThat(restriction.value()).isEqualTo("hasAnyAuthority('ADMIN')");
    }
}
