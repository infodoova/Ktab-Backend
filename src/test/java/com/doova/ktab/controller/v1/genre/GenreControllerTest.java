package com.doova.ktab.controller.v1.genre;

import com.doova.ktab.dto.genre.MainGenreDTO;
import com.doova.ktab.dto.genre.SubGenreDTO;
import com.doova.ktab.service.genre.GenreCommandService;
import com.doova.ktab.service.genre.GenreQueryService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GenreControllerTest {

    @Mock
    private GenreCommandService genreCommandService;

    @Mock
    private GenreQueryService genreQueryService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private GenreController genreController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(genreController).build();
        when(messageSource.getMessage(anyString(), any(), any(Locale.class))).thenReturn("Success");
    }

    @Test
    @DisplayName("getAllGenres_canonicalAndAliasEndpoints_bothReturnOk")
    void getAllGenres_canonicalAndAliasEndpoints_bothReturnOk() throws Exception {
        MainGenreDTO genre = new MainGenreDTO(
                1L,
                "أدب وروايات",
                List.of(
                        new SubGenreDTO(101L, "خيال علمي"),
                        new SubGenreDTO(102L, "غموض وإثارة")
                )
        );
        when(genreQueryService.getAllGenres(anyBoolean())).thenReturn(List.of(genre));

        // Test alias path /genres/getAllGenres
        mockMvc.perform(get("/genres/getAllGenres")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("أدب وروايات"))
                .andExpect(jsonPath("$.data[0].subGenres[0].id").value(101))
                .andExpect(jsonPath("$.data[0].subGenres[0].name").value("خيال علمي"))
                .andExpect(jsonPath("$.data[0].nameAr").doesNotExist())
                .andExpect(jsonPath("$.data[0].nameEn").doesNotExist())
                .andExpect(jsonPath("$.data[0].nameProvided").doesNotExist())
                .andExpect(jsonPath("$.data[0].subGenres[0].nameProvided").doesNotExist());

        // Test alias path /genres/viewAll
        mockMvc.perform(get("/genres/viewAll")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("أدب وروايات"));
    }

    @Test
    @DisplayName("getGenreById_canonicalAndAliasEndpoints_bothReturnOk")
    void getGenreById_canonicalAndAliasEndpoints_bothReturnOk() throws Exception {
        MainGenreDTO genre = new MainGenreDTO(
                1L,
                "أدب وروايات",
                List.of(new SubGenreDTO(101L, "خيال علمي"))
        );
        when(genreQueryService.getById(eq(1L), anyBoolean())).thenReturn(genre);

        // Test alias path /genres/getGenreById/1
        mockMvc.perform(get("/genres/getGenreById/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("أدب وروايات"))
                .andExpect(jsonPath("$.data.subGenres[0].id").value(101))
                .andExpect(jsonPath("$.data.subGenres[0].name").value("خيال علمي"));

        // Test canonical path /genres/1
        mockMvc.perform(get("/genres/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("أدب وروايات"));
    }
}
